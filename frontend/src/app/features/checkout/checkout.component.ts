import { Component, DestroyRef, ElementRef, OnInit, afterRenderEffect, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { delay, finalize } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { SeatResponse } from '../../core/models/catalog.model';
import { PassengerResponse } from '../../core/models/passenger.model';
import { PaymentResponse } from '../../core/models/payment.model';
import { BookingDraft, BookingDraftService } from '../../core/services/booking-draft.service';
import { BookingService } from '../../core/services/booking.service';
import { NotificationService } from '../../core/services/notification.service';
import { PassengerService } from '../../core/services/passenger.service';
import { PaymentService } from '../../core/services/payment.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { idNumberValidator } from '../../core/validators/id-number.validator';
import { luhnValidator } from '../../core/validators/luhn.validator';
import { CountdownComponent } from '../../shared/components/countdown/countdown.component';
import { ETicketCardComponent } from '../../shared/components/e-ticket-card/e-ticket-card.component';
import { QrCodeComponent } from '../../shared/components/qr-code/qr-code.component';

type CheckoutStep = 'assign' | 'payment';
type PaymentMethod = 'card' | 'pix';
type PaymentState = 'idle' | 'processing' | 'requires3ds' | 'succeeded' | 'declined';

/** Simulated gateway's own test PANs (see backend CardDeclineSimulator) — surfaced so a demo user can actually exercise both outcomes. */
const DEMO_APPROVE_CARD = '4242 4242 4242 4242';
const DEMO_DECLINE_CARD = '4000 0000 0000 0002';
/** Stripe's real "requires authentication" test PAN — lands the payment in PENDING_3DS instead of an immediate approve/decline. */
const DEMO_THREE_DS_CARD = '4000 0025 0000 3155';
/** The simulated authentication code confirmThreeDs() accepts — see backend PaymentServiceImpl.THREE_DS_VALID_CODE. */
const DEMO_THREE_DS_CODE = '123456';

/** Static, obviously-fake demo Pix key — no real payment provider is wired up. */
const DEMO_PIX_KEY = 'ticketwave-demo-pix@example.com';

/** A short, perceptible minimum "processing" time on top of whatever the (synchronous, usually near-instant) API actually takes. */
const MIN_PROCESSING_MS = 1200;

@Component({
  selector: 'tw-checkout',
  imports: [ReactiveFormsModule, RouterLink, CountdownComponent, ETicketCardComponent, QrCodeComponent],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.scss',
})
export class CheckoutComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly bookingDraftService = inject(BookingDraftService);
  private readonly passengerService = inject(PassengerService);
  private readonly bookingService = inject(BookingService);
  private readonly paymentService = inject(PaymentService);
  private readonly scheduleService = inject(ScheduleService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly demoApproveCard = DEMO_APPROVE_CARD;
  readonly demoDeclineCard = DEMO_DECLINE_CARD;
  readonly demoThreeDsCard = DEMO_THREE_DS_CARD;
  readonly demoThreeDsCode = DEMO_THREE_DS_CODE;
  readonly demoPixKey = DEMO_PIX_KEY;

  readonly draft = signal<BookingDraft | null>(null);
  readonly passengers = signal<PassengerResponse[]>([]);
  readonly seatAssignments = signal<Record<number, number | null>>({});
  readonly step = signal<CheckoutStep>('assign');
  readonly booking = signal<BookingDetailResponse | null>(null);
  readonly payment = signal<PaymentResponse | null>(null);
  readonly creatingBooking = signal(false);
  readonly addingPassenger = signal(false);
  readonly submittingThreeDs = signal(false);

  readonly paymentMethod = signal<PaymentMethod>('card');
  readonly paymentState = signal<PaymentState>('idle');
  readonly holdExpired = signal(false);

  /** Re-fetched after createBooking so the payment-step countdown reflects the fresh hold TTL createBooking's holdSeat() renews, not the stale one from seat-selection. */
  private readonly paymentStepSeats = signal<SeatResponse[] | null>(null);

  /**
   * Identifies the current payment *attempt*, reused across retries of that same attempt so the
   * backend's reference-based idempotency actually applies (see PaymentServiceImpl.recordPayment).
   * Without this, a submit that succeeded server-side but errored client-side (dropped response,
   * network blip) would get a brand-new reference on retry, skip the idempotency check entirely,
   * and hit the coarser "booking already confirmed" guard instead — a confusing 409 for what's
   * actually a completed payment. Only regenerated when the user starts a genuinely new attempt
   * (retryPayment, or a new booking).
   */
  private paymentReference = crypto.randomUUID();

  private readonly resultHeading = viewChild<ElementRef<HTMLElement>>('resultHeading');

  readonly allSeatsAssigned = computed(() => {
    const draft = this.draft();
    if (!draft) {
      return false;
    }
    const assignments = this.seatAssignments();
    return draft.seats.every((seat) => !!assignments[seat.id]);
  });

  readonly holdDeadline = computed(() => {
    const seats = this.step() === 'payment' ? (this.paymentStepSeats() ?? []) : (this.draft()?.seats ?? []);
    const deadlines = seats.map((seat) => seat.heldUntil).filter((value): value is string => value !== null);
    if (deadlines.length === 0) {
      return null;
    }
    return deadlines.reduce((earliest, current) => (current < earliest ? current : earliest));
  });

  readonly promoForm = this.fb.nonNullable.group({
    promoCode: [''],
  });

  readonly newPassengerForm = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    dob: ['', Validators.required],
    idType: ['passport', Validators.required],
    idNumber: ['', [Validators.required, idNumberValidator()]],
  });

  readonly cardForm = this.fb.nonNullable.group({
    cardholderName: ['', Validators.required],
    cardNumber: ['', [Validators.required, Validators.pattern(/^[0-9 ]{12,24}$/), luhnValidator()]],
    expiry: ['', [Validators.required, Validators.pattern(/^\d{2}\/\d{2}$/)]],
    cvc: ['', [Validators.required, Validators.pattern(/^\d{3,4}$/)]],
  });

  readonly threeDsForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  constructor() {
    afterRenderEffect(() => {
      const state = this.paymentState();
      if (state === 'succeeded' || state === 'declined' || state === 'requires3ds') {
        this.resultHeading()?.nativeElement.focus();
      }
    });
  }

  ngOnInit(): void {
    const draft = this.bookingDraftService.draft();
    if (!draft) {
      this.router.navigate(['/search']);
      return;
    }
    this.draft.set(draft);
    if (draft.promoCode) {
      this.promoForm.setValue({ promoCode: draft.promoCode });
    }

    this.passengerService
      .listMyPassengers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (passengers) => this.passengers.set(passengers),
      });

    this.newPassengerForm.controls.idType.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.newPassengerForm.controls.idNumber.updateValueAndValidity());
  }

  onAssignChange(seatId: number, event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.assignPassenger(seatId, value);
  }

  assignPassenger(seatId: number, passengerId: string): void {
    this.seatAssignments.update((current) => ({
      ...current,
      [seatId]: passengerId ? Number(passengerId) : null,
    }));
  }

  addPassenger(): void {
    if (this.newPassengerForm.invalid || this.addingPassenger()) {
      return;
    }

    this.addingPassenger.set(true);
    this.passengerService
      .createPassenger(this.newPassengerForm.getRawValue())
      .pipe(finalize(() => this.addingPassenger.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (passenger) => {
          this.passengers.update((current) => [...current, passenger]);
          this.newPassengerForm.reset({ fullName: '', dob: '', idType: 'passport', idNumber: '' });
        },
      });
  }

  createBooking(): void {
    const draft = this.draft();
    if (!draft || !this.allSeatsAssigned() || this.creatingBooking()) {
      return;
    }

    const assignments = this.seatAssignments();
    const promoCode = this.promoForm.getRawValue().promoCode.trim();

    this.creatingBooking.set(true);
    this.bookingService
      .createBooking({
        scheduleId: draft.schedule.scheduleId,
        seatSelections: draft.seats.map((seat) => ({
          seatId: seat.id,
          passengerId: assignments[seat.id]!,
        })),
        promoCode: promoCode || null,
      })
      .pipe(finalize(() => this.creatingBooking.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (booking) => {
          this.booking.set(booking);
          this.step.set('payment');
          this.paymentReference = crypto.randomUUID();
          this.refreshPaymentStepSeats(draft.schedule.scheduleId, booking);
        },
      });
  }

  private refreshPaymentStepSeats(scheduleId: number, booking: BookingDetailResponse): void {
    const bookedSeatIds = new Set(booking.items.map((item) => item.seatId));
    this.scheduleService
      .getSeats(scheduleId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (seats) => this.paymentStepSeats.set(seats.filter((seat) => bookedSeatIds.has(seat.id))),
      });
  }

  onHoldExpired(): void {
    if (this.paymentState() !== 'succeeded') {
      this.holdExpired.set(true);
    }
  }

  selectPaymentMethod(method: PaymentMethod): void {
    if (this.paymentState() === 'processing') {
      return;
    }
    this.paymentMethod.set(method);
  }

  formatCardNumberInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digitsOnly = input.value.replace(/\D/g, '').slice(0, 19);
    const grouped = (digitsOnly.match(/.{1,4}/g) ?? []).join(' ');
    this.cardForm.controls.cardNumber.setValue(grouped);
  }

  payWithCard(): void {
    if (this.cardForm.invalid) {
      this.cardForm.markAllAsTouched();
      return;
    }
    this.submitPayment('card', this.cardForm.getRawValue().cardNumber);
  }

  payWithPix(): void {
    this.submitPayment('pix', null);
  }

  copyPixKey(): void {
    navigator.clipboard
      ?.writeText(this.demoPixKey)
      .then(() => this.notifications.info('Pix key copied.'))
      .catch(() => this.notifications.error('Could not copy the Pix key.'));
  }

  private submitPayment(method: PaymentMethod, cardNumber: string | null): void {
    const booking = this.booking();
    if (!booking || this.paymentState() === 'processing' || this.holdExpired()) {
      return;
    }

    this.paymentState.set('processing');
    this.paymentService
      .recordPayment(booking.booking.id, {
        amount: booking.booking.totalAmount,
        method,
        reference: this.paymentReference,
        cardNumber,
      })
      .pipe(delay(MIN_PROCESSING_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (payment) => {
          this.payment.set(payment);
          if (payment.status === 'SUCCEEDED') {
            this.paymentState.set('succeeded');
            this.bookingDraftService.clear();
          } else if (payment.status === 'PENDING_3DS') {
            this.paymentState.set('requires3ds');
          } else {
            this.paymentState.set('declined');
          }
        },
        error: () => this.paymentState.set('idle'),
      });
  }

  confirmThreeDs(): void {
    const booking = this.booking();
    const payment = this.payment();
    if (!booking || !payment || this.threeDsForm.invalid || this.submittingThreeDs()) {
      this.threeDsForm.markAllAsTouched();
      return;
    }

    this.submittingThreeDs.set(true);
    this.paymentService
      .confirmThreeDs(booking.booking.id, payment.id, this.threeDsForm.getRawValue().code)
      .pipe(finalize(() => this.submittingThreeDs.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.payment.set(result);
          this.threeDsForm.reset({ code: '' });
          if (result.status === 'SUCCEEDED') {
            this.paymentState.set('succeeded');
            this.bookingDraftService.clear();
          } else {
            this.paymentState.set('declined');
          }
        },
      });
  }

  retryPayment(): void {
    this.payment.set(null);
    this.paymentState.set('idle');
    this.paymentReference = crypto.randomUUID();
    this.threeDsForm.reset({ code: '' });
  }

  viewBooking(): void {
    const booking = this.booking();
    if (booking) {
      this.router.navigate(['/bookings', booking.booking.id]);
    }
  }
}
