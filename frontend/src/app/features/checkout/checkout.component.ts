import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { PassengerResponse } from '../../core/models/passenger.model';
import { PaymentResponse } from '../../core/models/payment.model';
import { BookingDraft, BookingDraftService } from '../../core/services/booking-draft.service';
import { BookingService } from '../../core/services/booking.service';
import { PassengerService } from '../../core/services/passenger.service';
import { PaymentService } from '../../core/services/payment.service';

type CheckoutStep = 'assign' | 'payment';

@Component({
  selector: 'tw-checkout',
  imports: [ReactiveFormsModule],
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
  private readonly destroyRef = inject(DestroyRef);

  readonly draft = signal<BookingDraft | null>(null);
  readonly passengers = signal<PassengerResponse[]>([]);
  readonly seatAssignments = signal<Record<number, number | null>>({});
  readonly step = signal<CheckoutStep>('assign');
  readonly booking = signal<BookingDetailResponse | null>(null);
  readonly payment = signal<PaymentResponse | null>(null);
  readonly creatingBooking = signal(false);
  readonly payingSubmitting = signal(false);
  readonly addingPassenger = signal(false);

  readonly allSeatsAssigned = computed(() => {
    const draft = this.draft();
    if (!draft) {
      return false;
    }
    const assignments = this.seatAssignments();
    return draft.seats.every((seat) => !!assignments[seat.id]);
  });

  readonly promoForm = this.fb.nonNullable.group({
    promoCode: [''],
  });

  readonly newPassengerForm = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    dob: ['', Validators.required],
    idType: ['passport', Validators.required],
    idNumber: ['', Validators.required],
  });

  readonly paymentForm = this.fb.nonNullable.group({
    method: ['card', Validators.required],
    reference: [crypto.randomUUID(), Validators.required],
  });

  ngOnInit(): void {
    const draft = this.bookingDraftService.draft();
    if (!draft) {
      this.router.navigate(['/search']);
      return;
    }
    this.draft.set(draft);

    this.passengerService
      .listMyPassengers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (passengers) => this.passengers.set(passengers),
      });
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
        },
      });
  }

  pay(): void {
    const booking = this.booking();
    if (!booking || this.paymentForm.invalid || this.payingSubmitting()) {
      return;
    }

    this.payingSubmitting.set(true);
    this.paymentService
      .recordPayment(booking.booking.id, {
        amount: booking.booking.totalAmount,
        ...this.paymentForm.getRawValue(),
      })
      .pipe(finalize(() => this.payingSubmitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (payment) => {
          this.payment.set(payment);
          this.bookingDraftService.clear();
          this.router.navigate(['/bookings', booking.booking.id]);
        },
      });
  }
}
