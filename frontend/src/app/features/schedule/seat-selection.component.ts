import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { ScheduleSearchResult, SeatResponse } from '../../core/models/catalog.model';
import { AuthService } from '../../core/services/auth.service';
import { BookingDraftService } from '../../core/services/booking-draft.service';
import { BookingService } from '../../core/services/booking.service';
import { NotificationService } from '../../core/services/notification.service';
import { RescheduleContextService } from '../../core/services/reschedule-context.service';
import { ScheduleService } from '../../core/services/schedule.service';

@Component({
  selector: 'tw-seat-selection',
  imports: [DatePipe],
  templateUrl: './seat-selection.component.html',
  styleUrl: './seat-selection.component.scss',
})
export class SeatSelectionComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly scheduleService = inject(ScheduleService);
  private readonly bookingDraft = inject(BookingDraftService);
  private readonly bookingService = inject(BookingService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly rescheduleContext = inject(RescheduleContextService);
  private readonly notifications = inject(NotificationService);

  readonly loading = signal(true);
  readonly schedule = signal<ScheduleSearchResult | null>(null);
  readonly seats = signal<SeatResponse[]>([]);
  readonly selectedSeatIds = signal<Set<number>>(new Set());
  readonly rescheduling = signal(false);

  readonly isRescheduleMode = computed(() => this.rescheduleContext.context() !== null);

  readonly selectedSeats = computed(() =>
    this.seats().filter((seat) => this.selectedSeatIds().has(seat.id)),
  );

  readonly estimatedTotal = computed(() => {
    const schedule = this.schedule();
    if (!schedule) {
      return 0;
    }
    return this.selectedSeats().reduce((sum, seat) => sum + schedule.baseFare * seat.priceModifier, 0);
  });

  constructor() {
    const scheduleId = Number(this.route.snapshot.paramMap.get('id'));

    forkJoin({
      schedule: this.scheduleService.getSchedule(scheduleId),
      seats: this.scheduleService.getSeats(scheduleId),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ schedule, seats }) => {
          this.schedule.set(schedule);
          this.seats.set(seats);
          this.loading.set(false);
        },
      });
  }

  toggleSeat(seat: SeatResponse): void {
    if (seat.status !== 'AVAILABLE') {
      return;
    }

    this.selectedSeatIds.update((current) => {
      const next = new Set(current);
      if (next.has(seat.id)) {
        next.delete(seat.id);
      } else {
        next.add(seat.id);
      }
      return next;
    });
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

    this.bookingDraft.set({ schedule, seats: this.selectedSeats() });

    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/login'], { queryParams: { redirectTo: '/checkout' } });
      return;
    }

    this.router.navigate(['/checkout']);
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

    this.rescheduling.set(true);
    this.bookingService
      .rescheduleBooking(bookingId, {
        scheduleId: schedule.scheduleId,
        seatSelections: selectedSeats.map((seat, index) => ({
          seatId: seat.id,
          passengerId: passengerIds[index],
        })),
      })
      .pipe(finalize(() => this.rescheduling.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.rescheduleContext.clear();
          this.notifications.success('Booking rescheduled.');
          this.router.navigate(['/bookings', bookingId]);
        },
      });
  }
}
