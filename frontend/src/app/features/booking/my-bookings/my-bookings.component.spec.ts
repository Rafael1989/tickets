import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BookingSearchResult, BookingStatus } from '../../../core/models/booking.model';
import { BookingService } from '../../../core/services/booking.service';
import { MyBookingsComponent } from './my-bookings.component';

describe('MyBookingsComponent', () => {
  let fixture: ComponentFixture<MyBookingsComponent>;
  let component: MyBookingsComponent;
  let bookingService: BookingService;

  const HOUR = 60 * 60 * 1000;

  function bookingWith(
    overrides: Partial<BookingSearchResult> & { bookingId: number; status: BookingStatus; departureTime: string },
  ): BookingSearchResult {
    return {
      pnr: 'ABC234',
      totalAmount: 50,
      bookingTime: '2026-08-01T00:00:00Z',
      customerUsername: 'alice',
      customerEmail: 'alice@example.com',
      origin: 'NYC',
      destination: 'Boston',
      ...overrides,
    };
  }

  async function createComponent(result: BookingSearchResult[] | 'error') {
    await TestBed.configureTestingModule({
      imports: [MyBookingsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    bookingService = TestBed.inject(BookingService);
    vi.spyOn(bookingService, 'listMyBookings').mockReturnValue(
      result === 'error' ? throwError(() => new Error('boom')) : of(result),
    );

    fixture = TestBed.createComponent(MyBookingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('loads the customer\'s bookings', async () => {
    await createComponent([bookingWith({ bookingId: 1, status: 'CONFIRMED', departureTime: '2026-12-01T00:00:00Z' })]);

    expect(component.loading()).toBe(false);
    expect(component.bookings()).toHaveLength(1);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('ABC234');
  });

  it('shows an empty state when there are no bookings', async () => {
    await createComponent([]);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('No bookings yet');
  });

  it('shows a retryable error when the request fails', async () => {
    await createComponent('error');

    expect(component.loadError()).toBe(true);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain("We couldn't load your bookings");
  });

  it('filters upcoming by departure time, not just status', async () => {
    const future = new Date(Date.now() + 48 * HOUR).toISOString();
    const past = new Date(Date.now() - 48 * HOUR).toISOString();
    await createComponent([
      bookingWith({ bookingId: 1, status: 'CONFIRMED', departureTime: future, pnr: 'FUTURE' }),
      bookingWith({ bookingId: 2, status: 'CONFIRMED', departureTime: past, pnr: 'PAST01' }),
    ]);

    component.setFilter('upcoming');
    expect(component.visibleBookings().map((b) => b.pnr)).toEqual(['FUTURE']);

    component.setFilter('past');
    expect(component.visibleBookings().map((b) => b.pnr)).toEqual(['PAST01']);
  });

  it('counts a cancelled booking as past even when its departure is still ahead', async () => {
    const future = new Date(Date.now() + 48 * HOUR).toISOString();
    await createComponent([
      bookingWith({ bookingId: 1, status: 'CANCELLED', departureTime: future, pnr: 'CANX01' }),
    ]);

    expect(component.upcomingCount()).toBe(0);

    component.setFilter('past');
    expect(component.visibleBookings().map((b) => b.pnr)).toEqual(['CANX01']);
  });

  it('renders a readable label for machine-style statuses', async () => {
    await createComponent([]);

    expect(component.statusLabel('PAYMENT_PROCESSING')).toBe('Processing payment');
    expect(component.statusLabel('INITIATED')).toBe('Awaiting payment');
    expect(component.statusLabel('CONFIRMED')).toBe('Confirmed');
  });
});
