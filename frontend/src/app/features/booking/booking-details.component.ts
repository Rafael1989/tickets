import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, finalize, forkJoin, interval } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../core/models/catalog.model';
import { PassengerResponse } from '../../core/models/passenger.model';
import { RefundResponse } from '../../core/models/payment.model';
import { BookingService } from '../../core/services/booking.service';
import { NotificationService } from '../../core/services/notification.service';
import { PassengerService } from '../../core/services/passenger.service';
import { RefundService } from '../../core/services/refund.service';
import { RescheduleContextService } from '../../core/services/reschedule-context.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { ETicketCardComponent } from '../../shared/components/e-ticket-card/e-ticket-card.component';
import { CancellationWizardComponent } from './cancellation-wizard/cancellation-wizard.component';
import { RefundStatusTrackerComponent } from './refund-status-tracker/refund-status-tracker.component';

/** How often to re-poll a PENDING refund's status — support/admin resolve these out-of-band, so this is the only way the customer sees an approval/rejection without reloading the page. */
const REFUND_POLL_INTERVAL_MS = 10_000;

@Component({
  selector: 'tw-booking-details',
  imports: [DatePipe, RouterLink, ETicketCardComponent, CancellationWizardComponent, RefundStatusTrackerComponent],
  templateUrl: './booking-details.component.html',
  styleUrl: './booking-details.component.scss',
})
export class BookingDetailsComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly bookingService = inject(BookingService);
  private readonly scheduleService = inject(ScheduleService);
  private readonly passengerService = inject(PassengerService);
  private readonly refundService = inject(RefundService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly rescheduleContext = inject(RescheduleContextService);
  private readonly router = inject(Router);

  private readonly cancelTrigger = viewChild<ElementRef<HTMLButtonElement>>('cancelTrigger');
  private refundPollSub: Subscription | null = null;

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly detail = signal<BookingDetailResponse | null>(null);
  readonly schedule = signal<ScheduleSearchResult | null>(null);
  readonly seats = signal<SeatResponse[]>([]);
  readonly passengers = signal<PassengerResponse[]>([]);
  readonly refund = signal<RefundResponse | null>(null);
  readonly showCancellationWizard = signal(false);

  /**
   * Whether the cancellation-proximity window still allows a CONFIRMED booking to be rescheduled
   * at all (RescheduleServiceImpl gates this on the *current* schedule's departure time, not the
   * target). Defaults true so INITIATED bookings — which skip this check entirely — are never
   * affected; set from the refund-quote's `eligible` flag once fetched for CONFIRMED bookings,
   * since it's driven by the same RefundPolicyService window for the same reason.
   */
  readonly rescheduleQuoteEligible = signal(true);

  readonly canRefund = computed(() => this.detail()?.booking.status === 'CONFIRMED' && !this.refund());
  readonly canReschedule = computed(() => {
    const status = this.detail()?.booking.status;
    return (status === 'INITIATED' || status === 'CONFIRMED') && !this.refund() && this.rescheduleQuoteEligible();
  });
  /** Shown in place of the reschedule card so the user learns this before picking a whole new schedule/seats, not after. */
  readonly rescheduleTooCloseToDeparture = computed(
    () => this.detail()?.booking.status === 'CONFIRMED' && !this.refund() && !this.rescheduleQuoteEligible(),
  );

  constructor() {
    const bookingId = Number(this.route.snapshot.paramMap.get('id'));

    this.bookingService
      .getBooking(bookingId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.detail.set(detail);
          if (detail.booking.status === 'CONFIRMED') {
            this.refundService
              .getRefundQuote(bookingId)
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe({ next: (quote) => this.rescheduleQuoteEligible.set(quote.eligible) });
          }
          forkJoin({
            schedule: this.scheduleService.getSchedule(detail.booking.scheduleId),
            seats: this.scheduleService.getSeats(detail.booking.scheduleId),
            passengers: this.passengerService.listMyPassengers(),
            refunds: this.refundService.listRefundsForBooking(bookingId),
          })
            .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: ({ schedule, seats, passengers, refunds }) => {
                this.schedule.set(schedule);
                this.seats.set(seats);
                this.passengers.set(passengers);
                this.applyRefund(refunds[0] ?? null, bookingId);
              },
            });
        },
        error: () => {
          this.loading.set(false);
          this.loadError.set(true);
        },
      });

    this.destroyRef.onDestroy(() => this.refundPollSub?.unsubscribe());
  }

  seatNumber(seatId: number): string {
    return this.seats().find((seat) => seat.id === seatId)?.seatNumber ?? `#${seatId}`;
  }

  passengerName(passengerId: number): string {
    return this.passengers().find((p) => p.id === passengerId)?.fullName ?? `#${passengerId}`;
  }

  openCancellationWizard(): void {
    if (!this.canRefund()) {
      return;
    }
    this.showCancellationWizard.set(true);
  }

  closeCancellationWizard(): void {
    this.showCancellationWizard.set(false);
    this.cancelTrigger()?.nativeElement.focus();
  }

  /**
   * Deliberately leaves the booking's status alone: the backend keeps it CONFIRMED until support
   * approves the refund (see RefundServiceImpl.initiateRefund), so optimistically flipping it to
   * CANCELLED here would show the customer a cancelled trip that a rejection would then contradict.
   */
  onCancelled(refund: RefundResponse): void {
    this.applyRefund(refund, this.detail()?.booking.id);
    this.notifications.success(
      'Cancellation requested. Your booking stays active until support reviews it.',
    );
    this.closeCancellationWizard();
  }

  /** Records the current refund and, while it's still PENDING, starts polling for a support/admin decision. */
  private applyRefund(refund: RefundResponse | null, bookingId: number | undefined): void {
    this.refund.set(refund);
    this.refundPollSub?.unsubscribe();
    this.refundPollSub = null;

    if (refund?.status !== 'PENDING' || !bookingId) {
      return;
    }

    this.refundPollSub = interval(REFUND_POLL_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.refundService.listRefundsForBooking(bookingId).subscribe({
          next: (refunds) => {
            const latest = refunds[0] ?? null;
            this.refund.set(latest);
            if (latest?.status !== 'PENDING') {
              this.refundPollSub?.unsubscribe();
              this.refundPollSub = null;
            }
          },
        });
      });
  }

  startReschedule(): void {
    const detail = this.detail();
    if (!detail) {
      return;
    }
    this.rescheduleContext.start(
      detail.booking.id,
      detail.items.map((item) => item.passengerId),
      detail.booking.status === 'CONFIRMED',
    );
    this.router.navigate(['/search']);
  }
}
