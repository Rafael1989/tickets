import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { BookingDetailResponse } from '../../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../../core/models/catalog.model';
import { BookingService } from '../../../core/services/booking.service';
import { ScheduleService } from '../../../core/services/schedule.service';
import { ETicketCardComponent } from '../../../shared/components/e-ticket-card/e-ticket-card.component';

/**
 * Guest "find my booking" page — no account needed. PNR + the email on the
 * booking act as a two-factor lookup (GET /api/bookings/pnr/{pnr}/lookup),
 * a distinct, public endpoint from the authenticated staff-only
 * GET /api/bookings/pnr/{pnr}. Read-only: guests can view but not
 * cancel/reschedule here, since those actions require an authenticated
 * account.
 */
@Component({
  selector: 'tw-guest-lookup',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, ETicketCardComponent],
  templateUrl: './guest-lookup.component.html',
  styleUrl: './guest-lookup.component.scss',
})
export class GuestLookupComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bookingService = inject(BookingService);
  private readonly scheduleService = inject(ScheduleService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(false);
  readonly searched = signal(false);
  readonly notFound = signal(false);
  readonly detail = signal<BookingDetailResponse | null>(null);
  readonly schedule = signal<ScheduleSearchResult | null>(null);
  readonly seats = signal<SeatResponse[]>([]);

  readonly form = this.fb.nonNullable.group({
    pnr: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
  });

  search(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }

    const { pnr, email } = this.form.getRawValue();
    this.loading.set(true);
    this.notFound.set(false);
    this.detail.set(null);

    this.bookingService
      .lookupByPnrAndEmail(pnr, email)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.detail.set(detail);
          forkJoin({
            schedule: this.scheduleService.getSchedule(detail.booking.scheduleId),
            seats: this.scheduleService.getSeats(detail.booking.scheduleId),
          })
            .pipe(
              finalize(() => {
                this.loading.set(false);
                this.searched.set(true);
              }),
              takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(({ schedule, seats }) => {
              this.schedule.set(schedule);
              this.seats.set(seats);
            });
        },
        error: () => {
          this.loading.set(false);
          this.searched.set(true);
          this.notFound.set(true);
        },
      });
  }

  seatNumber(seatId: number): string {
    return this.seats().find((seat) => seat.id === seatId)?.seatNumber ?? `#${seatId}`;
  }
}
