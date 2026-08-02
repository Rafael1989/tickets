import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { BookingSearchResult, BookingStatus } from '../../../core/models/booking.model';
import { BookingService } from '../../../core/services/booking.service';

type StatusFilter = 'all' | 'upcoming' | 'past';

@Component({
  selector: 'tw-my-bookings',
  imports: [DatePipe, RouterLink],
  templateUrl: './my-bookings.component.html',
  styleUrl: './my-bookings.component.scss',
})
export class MyBookingsComponent implements OnInit {
  private readonly bookingService = inject(BookingService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly bookings = signal<BookingSearchResult[]>([]);
  readonly filter = signal<StatusFilter>('all');

  /**
   * "Upcoming" is departure-based rather than status-based: a CONFIRMED booking whose trip has
   * already departed is history to a customer, even though its status never changes afterward.
   * CANCELLED is always past regardless of departure — there's no trip left to take.
   */
  readonly visibleBookings = computed(() => {
    const all = this.bookings();
    const now = Date.now();
    switch (this.filter()) {
      case 'upcoming':
        return all.filter((b) => b.status !== 'CANCELLED' && new Date(b.departureTime).getTime() >= now);
      case 'past':
        return all.filter((b) => b.status === 'CANCELLED' || new Date(b.departureTime).getTime() < now);
      default:
        return all;
    }
  });

  readonly upcomingCount = computed(
    () =>
      this.bookings().filter((b) => b.status !== 'CANCELLED' && new Date(b.departureTime).getTime() >= Date.now())
        .length,
  );

  ngOnInit(): void {
    this.load();
  }

  retryLoad(): void {
    this.load();
  }

  setFilter(filter: StatusFilter): void {
    this.filter.set(filter);
  }

  statusLabel(status: BookingStatus): string {
    switch (status) {
      case 'PAYMENT_PROCESSING':
        return 'Processing payment';
      case 'INITIATED':
        return 'Awaiting payment';
      default:
        return status.charAt(0) + status.slice(1).toLowerCase();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.bookingService
      .listMyBookings()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (bookings) => this.bookings.set(bookings),
        error: () => this.loadError.set(true),
      });
  }
}
