import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BookingDetailResponse } from '../../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../../core/models/catalog.model';
import { BookingService } from '../../../core/services/booking.service';
import { ScheduleService } from '../../../core/services/schedule.service';
import { GuestLookupComponent } from './guest-lookup.component';

describe('GuestLookupComponent', () => {
  let fixture: ComponentFixture<GuestLookupComponent>;
  let component: GuestLookupComponent;
  let bookingService: BookingService;
  let scheduleService: ScheduleService;

  const seat: SeatResponse = {
    id: 1,
    scheduleId: 1,
    seatNumber: '1A',
    seatClass: 'economy',
    status: 'BOOKED',
    priceModifier: 1,
    estimatedFare: 20,
    heldUntil: null,
    heldByMe: false,
  };

  const schedule: ScheduleSearchResult = {
    scheduleId: 1,
    routeId: 1,
    type: 'BUS',
    origin: 'NYC',
    destination: 'Boston',
    venue: null,
    departureTime: '2026-08-10T00:00:00Z',
    arrivalTime: '2026-08-10T04:00:00Z',
    baseFare: 20,
    currency: 'USD',
    status: 'SCHEDULED',
    availableSeats: 2,
  };

  function detailWith(status: 'INITIATED' | 'CONFIRMED' | 'CANCELLED'): BookingDetailResponse {
    return {
      booking: {
        id: 500,
        userId: 1,
        scheduleId: 1,
        pnr: 'ABC123',
        bookingTime: '2026-01-01T00:00:00Z',
        status,
        totalAmount: 20,
        promoCode: null,
      },
      items: [{ id: 1, bookingId: 500, seatId: 1, passengerId: 100, fare: 20 }],
    };
  }

  async function createComponent(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [GuestLookupComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    bookingService = TestBed.inject(BookingService);
    scheduleService = TestBed.inject(ScheduleService);
    vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of([seat]));
    vi.spyOn(scheduleService, 'getSchedule').mockReturnValue(of(schedule));

    fixture = TestBed.createComponent(GuestLookupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function fillForm(pnr: string, email: string): void {
    component.form.setValue({ pnr, email });
  }

  it('does not submit when the form is invalid', async () => {
    await createComponent();
    const spy = vi.spyOn(bookingService, 'lookupByPnrAndEmail');

    component.search();

    expect(spy).not.toHaveBeenCalled();
  });

  it('loads the booking, schedule, and seats on a successful lookup', async () => {
    await createComponent();
    vi.spyOn(bookingService, 'lookupByPnrAndEmail').mockReturnValue(of(detailWith('CONFIRMED')));
    fillForm('ABC123', 'alice@example.com');

    component.search();

    expect(bookingService.lookupByPnrAndEmail).toHaveBeenCalledWith('ABC123', 'alice@example.com');
    expect(component.loading()).toBe(false);
    expect(component.searched()).toBe(true);
    expect(component.notFound()).toBe(false);
    expect(component.detail()?.booking.pnr).toBe('ABC123');
    expect(component.schedule()).toEqual(schedule);
    expect(component.seatNumber(1)).toBe('1A');
  });

  it('sets notFound when the PNR/email pair does not match', async () => {
    await createComponent();
    vi.spyOn(bookingService, 'lookupByPnrAndEmail').mockReturnValue(throwError(() => new Error('not found')));
    fillForm('ZZZ999', 'nobody@example.com');

    component.search();

    expect(component.loading()).toBe(false);
    expect(component.searched()).toBe(true);
    expect(component.notFound()).toBe(true);
    expect(component.detail()).toBeNull();
  });

  it('renders the e-ticket card for a CONFIRMED booking', async () => {
    await createComponent();
    vi.spyOn(bookingService, 'lookupByPnrAndEmail').mockReturnValue(of(detailWith('CONFIRMED')));
    fillForm('ABC123', 'alice@example.com');

    component.search();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('tw-e-ticket-card')).not.toBeNull();
  });

  it('renders the plain items table for a non-CONFIRMED booking', async () => {
    await createComponent();
    vi.spyOn(bookingService, 'lookupByPnrAndEmail').mockReturnValue(of(detailWith('INITIATED')));
    fillForm('ABC123', 'alice@example.com');

    component.search();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('tw-e-ticket-card')).toBeNull();
    expect(root.querySelector('.item-table')).not.toBeNull();
  });

  it('renders the not-found message when the lookup fails', async () => {
    await createComponent();
    vi.spyOn(bookingService, 'lookupByPnrAndEmail').mockReturnValue(throwError(() => new Error('not found')));
    fillForm('ZZZ999', 'nobody@example.com');

    component.search();
    fixture.detectChanges();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain("couldn't find a booking");
  });
});
