import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, finalize, forkJoin, interval } from 'rxjs';
import { RescheduleQuoteResponse } from '../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../core/models/catalog.model';
import { PromoValidationResponse } from '../../core/models/promo.model';
import { AuthService } from '../../core/services/auth.service';
import { BookingDraftService } from '../../core/services/booking-draft.service';
import { BookingService } from '../../core/services/booking.service';
import { NotificationService } from '../../core/services/notification.service';
import { PromoService } from '../../core/services/promo.service';
import { RescheduleContextService } from '../../core/services/reschedule-context.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { CountdownComponent } from '../../shared/components/countdown/countdown.component';

type FareSettlementMethod = 'card' | 'pix';

const POLL_INTERVAL_MS = 6000;

/** economy/general read as Standard; business/vip read as Premium — the only
 * two tiers the current seed data actually produces. "Extra legroom" has its
 * own CSS hook below for the day a seatClass value like that exists. */
function seatTier(seatClass: string): 'standard' | 'premium' | 'extra-legroom' {
  const normalized = seatClass.toLowerCase();
  if (normalized.includes('legroom')) {
    return 'extra-legroom';
  }
  if (normalized === 'business' || normalized === 'vip') {
    return 'premium';
  }
  return 'standard';
}

@Component({
  selector: 'tw-seat-selection',
  imports: [DatePipe, ReactiveFormsModule, CountdownComponent],
  templateUrl: './seat-selection.component.html',
  styleUrl: './seat-selection.component.scss',
})
export class SeatSelectionComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly scheduleService = inject(ScheduleService);
  private readonly bookingDraft = inject(BookingDraftService);
  private readonly bookingService = inject(BookingService);
  private readonly promoService = inject(PromoService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly rescheduleContext = inject(RescheduleContextService);
  private readonly notifications = inject(NotificationService);

  private readonly scheduleId = Number(this.route.snapshot.paramMap.get('id'));
  private pollSub: Subscription | null = null;

  readonly loading = signal(true);
  readonly schedule = signal<ScheduleSearchResult | null>(null);
  readonly seats = signal<SeatResponse[]>([]);
  readonly guestSelectedSeatIds = signal<Set<number>>(new Set());
  readonly pendingSeatId = signal<number | null>(null);
  readonly rescheduling = signal(false);

  readonly isAuthenticated = this.auth.isAuthenticated;
  readonly isRescheduleMode = computed(() => this.rescheduleContext.context() !== null);
  readonly requiresFareSettlement = computed(() => this.rescheduleContext.context()?.requiresFareSettlement ?? false);

  readonly rescheduleQuote = signal<RescheduleQuoteResponse | null>(null);
  readonly rescheduleQuoteLoading = signal(false);
  readonly rescheduleQuoteError = signal<string | null>(null);
  readonly fareSettlementMethod = signal<FareSettlementMethod>('card');
  private lastQuotedKey: string | null = null;

  readonly selectedSeats = computed(() =>
    this.isAuthenticated()
      ? this.seats().filter((seat) => seat.heldByMe)
      : this.seats().filter((seat) => this.guestSelectedSeatIds().has(seat.id)),
  );

  readonly subtotal = computed(() =>
    this.selectedSeats().reduce((sum, seat) => sum + (seat.estimatedFare ?? 0), 0),
  );

  readonly appliedPromo = signal<PromoValidationResponse | null>(null);
  readonly promoLoading = signal(false);
  readonly promoError = signal<string | null>(null);

  readonly total = computed(() => {
    const promo = this.appliedPromo();
    if (!promo) {
      return this.subtotal();
    }
    return Math.max(0, this.subtotal() - promo.discountAmount);
  });

  readonly promoForm = this.fb.nonNullable.group({
    promoCode: [''],
  });

  readonly fareSettlementForm = this.fb.nonNullable.group({
    cardNumber: ['', [Validators.required, Validators.pattern(/^[0-9 ]{12,24}$/)]],
  });

  readonly seatTier = seatTier;

  constructor() {
    effect(() => {
      const context = this.rescheduleContext.context();
      const schedule = this.schedule();
      if (!context?.requiresFareSettlement || !schedule) {
        return;
      }

      const seats = this.selectedSeats();
      if (seats.length === 0 || seats.length !== context.passengerIds.length) {
        this.rescheduleQuote.set(null);
        this.lastQuotedKey = null;
        return;
      }

      const seatIds = seats.map((seat) => seat.id).sort((a, b) => a - b);
      const key = `${schedule.scheduleId}:${seatIds.join(',')}`;
      if (key === this.lastQuotedKey) {
        return;
      }
      this.lastQuotedKey = key;
      this.fetchRescheduleQuote(context.bookingId, schedule.scheduleId, seatIds);
    });

    forkJoin({
      schedule: this.scheduleService.getSchedule(this.scheduleId),
      seats: this.scheduleService.getSeats(this.scheduleId),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ schedule, seats }) => {
          this.schedule.set(schedule);
          this.seats.set(seats);
          this.loading.set(false);
          this.startPolling();
        },
      });

    this.destroyRef.onDestroy(() => this.pollSub?.unsubscribe());
  }

  private startPolling(): void {
    this.pollSub = interval(POLL_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshSeats());
  }

  private refreshSeats(): void {
    this.scheduleService.getSeats(this.scheduleId).subscribe({
      next: (fresh) => {
        if (!this.isAuthenticated()) {
          this.reconcileGuestSelectionAgainst(fresh);
        }
        this.seats.set(fresh);
      },
    });
  }

  private reconcileGuestSelectionAgainst(fresh: SeatResponse[]): void {
    const stillAvailable = new Map(fresh.map((seat) => [seat.id, seat.status === 'AVAILABLE']));
    const lost: SeatResponse[] = [];

    this.guestSelectedSeatIds.update((current) => {
      const next = new Set<number>();
      for (const id of current) {
        if (stillAvailable.get(id)) {
          next.add(id);
        } else {
          const seat = this.seats().find((s) => s.id === id);
          if (seat) {
            lost.push(seat);
          }
        }
      }
      return next;
    });

    for (const seat of lost) {
      this.notifications.error(`Seat ${seat.seatNumber} was just taken by another user.`);
    }
  }

  /** A seat's own hold countdown just hit zero — refresh now instead of waiting for the next poll tick. */
  onSeatHoldExpired(): void {
    this.refreshSeats();
  }

  toggleSeat(seat: SeatResponse): void {
    if (this.pendingSeatId() === seat.id) {
      return;
    }

    if (!this.isAuthenticated()) {
      if (seat.status !== 'AVAILABLE') {
        return;
      }
      this.guestSelectedSeatIds.update((current) => {
        const next = new Set(current);
        if (next.has(seat.id)) {
          next.delete(seat.id);
        } else {
          next.add(seat.id);
        }
        return next;
      });
      return;
    }

    if (seat.heldByMe) {
      this.releaseSeat(seat);
    } else if (seat.status === 'AVAILABLE') {
      this.holdSeat(seat);
    }
  }

  private holdSeat(seat: SeatResponse): void {
    this.pendingSeatId.set(seat.id);
    this.scheduleService
      .holdSeat(this.scheduleId, seat.id)
      .pipe(finalize(() => this.pendingSeatId.set(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => this.mergeSeat(updated),
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 409) {
            this.notifications.error(`Seat ${seat.seatNumber} was just taken by another user.`);
            this.refreshSeats();
          }
        },
      });
  }

  private releaseSeat(seat: SeatResponse): void {
    this.pendingSeatId.set(seat.id);
    this.scheduleService
      .releaseSeat(this.scheduleId, seat.id)
      .pipe(finalize(() => this.pendingSeatId.set(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () =>
          this.mergeSeat({ ...seat, status: 'AVAILABLE', heldByMe: false, heldUntil: null }),
      });
  }

  private mergeSeat(updated: SeatResponse): void {
    this.seats.update((current) => current.map((seat) => (seat.id === updated.id ? updated : seat)));
  }

  validatePromo(): void {
    const code = this.promoForm.getRawValue().promoCode.trim();
    if (!code || this.promoLoading() || this.subtotal() === 0) {
      return;
    }

    this.promoLoading.set(true);
    this.promoError.set(null);
    this.promoService
      .validate({ code, subtotal: this.subtotal() })
      .pipe(finalize(() => this.promoLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => this.appliedPromo.set(result),
        error: (error: unknown) => {
          const message =
            error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
              ? error.error.message
              : 'This promo code could not be applied.';
          this.promoError.set(message);
        },
      });
  }

  clearPromo(): void {
    this.appliedPromo.set(null);
    this.promoError.set(null);
    this.promoForm.reset({ promoCode: '' });
  }

  continueToCheckout(): void {
    const schedule = this.schedule();
    if (!schedule || this.selectedSeats().length === 0) {
      return;
    }

    const rescheduleContext = this.rescheduleContext.context();
    if (rescheduleContext) {
      this.confirmReschedule(rescheduleContext.bookingId, rescheduleContext.passengerIds, schedule);
      return;
    }

    this.bookingDraft.set({
      schedule,
      seats: this.selectedSeats(),
      promoCode: this.appliedPromo()?.code ?? null,
    });

    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/login'], { queryParams: { redirectTo: '/checkout' } });
      return;
    }

    this.router.navigate(['/checkout']);
  }

  selectFareSettlementMethod(method: FareSettlementMethod): void {
    this.fareSettlementMethod.set(method);
  }

  private fetchRescheduleQuote(bookingId: number, scheduleId: number, seatIds: number[]): void {
    this.rescheduleQuoteLoading.set(true);
    this.rescheduleQuoteError.set(null);
    this.bookingService
      .getRescheduleQuote(bookingId, scheduleId, seatIds)
      .pipe(finalize(() => this.rescheduleQuoteLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (quote) => this.rescheduleQuote.set(quote),
        error: () => {
          this.rescheduleQuote.set(null);
          this.rescheduleQuoteError.set("Couldn't calculate the fare difference for this selection.");
        },
      });
  }

  private confirmReschedule(bookingId: number, passengerIds: number[], schedule: ScheduleSearchResult): void {
    const selectedSeats = this.selectedSeats();
    if (selectedSeats.length !== passengerIds.length) {
      this.notifications.error(
        `Select exactly ${passengerIds.length} seat(s) to match this booking's passengers.`,
      );
      return;
    }

    if (this.rescheduling()) {
      return;
    }

    const requiresSettlement = this.requiresFareSettlement();
    const quote = this.rescheduleQuote();

    if (requiresSettlement) {
      if (this.rescheduleQuoteLoading() || !quote) {
        this.notifications.error('Please wait for the fare difference to finish calculating.');
        return;
      }
      if (!quote.eligible) {
        this.notifications.error('This booking is too close to departure to reschedule online.');
        return;
      }
      if (quote.paymentRequired && this.fareSettlementMethod() === 'card' && this.fareSettlementForm.invalid) {
        this.fareSettlementForm.markAllAsTouched();
        this.notifications.error('Enter a valid card number to continue.');
        return;
      }
    }

    const paymentRequired = requiresSettlement && (quote?.paymentRequired ?? false);
    const method = this.fareSettlementMethod();

    this.rescheduling.set(true);
    this.bookingService
      .rescheduleBooking(bookingId, {
        scheduleId: schedule.scheduleId,
        seatSelections: selectedSeats.map((seat, index) => ({
          seatId: seat.id,
          passengerId: passengerIds[index],
        })),
        paymentMethod: paymentRequired ? method : null,
        paymentReference: paymentRequired ? crypto.randomUUID() : null,
        cardNumber: paymentRequired && method === 'card' ? this.fareSettlementForm.getRawValue().cardNumber : null,
      })
      .pipe(finalize(() => this.rescheduling.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.rescheduleContext.clear();
          if (paymentRequired) {
            this.notifications.success('Booking rescheduled — the fare difference was charged.');
          } else if (requiresSettlement && (quote?.fareDifference ?? 0) < 0) {
            this.notifications.success('Booking rescheduled — a credit refund was issued for the difference.');
          } else {
            this.notifications.success('Booking rescheduled.');
          }
          this.router.navigate(['/bookings', bookingId]);
        },
      });
  }
}
