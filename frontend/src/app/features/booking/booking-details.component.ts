import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../core/models/catalog.model';
import { PassengerResponse } from '../../core/models/passenger.model';
import { RefundResponse } from '../../core/models/payment.model';
import { BookingService } from '../../core/services/booking.service';
import { NotificationService } from '../../core/services/notification.service';
import { PassengerService } from '../../core/services/passenger.service';
import { RescheduleContextService } from '../../core/services/reschedule-context.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { ETicketCardComponent } from '../../shared/components/e-ticket-card/e-ticket-card.component';
import { CancellationWizardComponent } from './cancellation-wizard/cancellation-wizard.component';
import { RefundStatusTrackerComponent } from './refund-status-tracker/refund-status-tracker.component';

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
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly rescheduleContext = inject(RescheduleContextService);
  private readonly router = inject(Router);

  private readonly cancelTrigger = viewChild<ElementRef<HTMLButtonElement>>('cancelTrigger');

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly detail = signal<BookingDetailResponse | null>(null);
  readonly schedule = signal<ScheduleSearchResult | null>(null);
  readonly seats = signal<SeatResponse[]>([]);
  readonly passengers = signal<PassengerResponse[]>([]);
  readonly refund = signal<RefundResponse | null>(null);
  readonly showCancellationWizard = signal(false);

  readonly canRefund = computed(() => this.detail()?.booking.status === 'CONFIRMED' && !this.refund());
  readonly canReschedule = computed(() => {
    const status = this.detail()?.booking.status;
    return (status === 'INITIATED' || status === 'CONFIRMED') && !this.refund();
  });

  constructor() {
    const bookingId = Number(this.route.snapshot.paramMap.get('id'));

    this.bookingService
      .getBooking(bookingId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.detail.set(detail);
          forkJoin({
            schedule: this.scheduleService.getSchedule(detail.booking.scheduleId),
            seats: this.scheduleService.getSeats(detail.booking.scheduleId),
            passengers: this.passengerService.listMyPassengers(),
          })
            .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: ({ schedule, seats, passengers }) => {
                this.schedule.set(schedule);
                this.seats.set(seats);
                this.passengers.set(passengers);
              },
            });
        },
        error: () => {
          this.loading.set(false);
          this.loadError.set(true);
        },
      });
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

  onCancelled(refund: RefundResponse): void {
    this.refund.set(refund);
    this.detail.update((current) =>
      current ? { ...current, booking: { ...current.booking, status: 'CANCELLED' } } : current,
    );
    this.notifications.success('Cancellation submitted. Our support team will review your refund shortly.');
    this.closeCancellationWizard();
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
