import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
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
import { RefundService } from '../../core/services/refund.service';
import { RescheduleContextService } from '../../core/services/reschedule-context.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { ETicketCardComponent } from '../../shared/components/e-ticket-card/e-ticket-card.component';

@Component({
  selector: 'tw-booking-details',
  imports: [DatePipe, RouterLink, ETicketCardComponent],
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

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly detail = signal<BookingDetailResponse | null>(null);
  readonly schedule = signal<ScheduleSearchResult | null>(null);
  readonly seats = signal<SeatResponse[]>([]);
  readonly passengers = signal<PassengerResponse[]>([]);
  readonly refund = signal<RefundResponse | null>(null);
  readonly requestingRefund = signal(false);

  readonly canRefund = computed(() => this.detail()?.booking.status === 'CONFIRMED' && !this.refund());
  readonly canReschedule = computed(() => this.detail()?.booking.status === 'INITIATED');

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

  requestRefund(): void {
    const detail = this.detail();
    if (!detail || this.requestingRefund()) {
      return;
    }

    this.requestingRefund.set(true);
    this.refundService
      .initiateRefund(detail.booking.id)
      .pipe(finalize(() => this.requestingRefund.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (refund) => {
          this.refund.set(refund);
          this.detail.update((current) =>
            current ? { ...current, booking: { ...current.booking, status: 'CANCELLED' } } : current,
          );
          this.notifications.success('Refund initiated.');
        },
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
    );
    this.router.navigate(['/search']);
  }
}
