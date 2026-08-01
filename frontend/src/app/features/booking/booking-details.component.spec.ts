import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../core/models/catalog.model';
import { PassengerResponse } from '../../core/models/passenger.model';
import { RefundResponse } from '../../core/models/payment.model';
import { BookingService } from '../../core/services/booking.service';
import { PassengerService } from '../../core/services/passenger.service';
import { RescheduleContextService } from '../../core/services/reschedule-context.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { BookingDetailsComponent } from './booking-details.component';

describe('BookingDetailsComponent', () => {
  let fixture: ComponentFixture<BookingDetailsComponent>;
  let component: BookingDetailsComponent;
  let bookingService: BookingService;
  let scheduleService: ScheduleService;
  let passengerService: PassengerService;
  let rescheduleContext: RescheduleContextService;
  let router: Router;

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

  const passenger: PassengerResponse = {
    id: 100,
    userId: 1,
    fullName: 'Alice Traveler',
    dob: '1990-01-01',
    idType: 'PASSPORT',
    idNumber: 'X123',
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

  async function createComponent(detail: BookingDetailResponse | 'error') {
    await TestBed.configureTestingModule({
      imports: [BookingDetailsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '500' }) } } },
      ],
    }).compileComponents();

    bookingService = TestBed.inject(BookingService);
    if (detail === 'error') {
      vi.spyOn(bookingService, 'getBooking').mockReturnValue(throwError(() => new Error('not found')));
    } else {
      vi.spyOn(bookingService, 'getBooking').mockReturnValue(of(detail));
    }

    scheduleService = TestBed.inject(ScheduleService);
    vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of([seat]));
    vi.spyOn(scheduleService, 'getSchedule').mockReturnValue(of(schedule));

    passengerService = TestBed.inject(PassengerService);
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([passenger]));

    rescheduleContext = TestBed.inject(RescheduleContextService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(BookingDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => localStorage.clear());

  it('loads booking, seats and passengers', async () => {
    await createComponent(detailWith('CONFIRMED'));

    expect(component.loading()).toBe(false);
    expect(component.detail()?.booking.pnr).toBe('ABC123');
    expect(component.seatNumber(1)).toBe('1A');
    expect(component.passengerName(100)).toBe('Alice Traveler');
  });

  it('sets loadError when the booking cannot be fetched', async () => {
    await createComponent('error');

    expect(component.loading()).toBe(false);
    expect(component.loadError()).toBe(true);
  });

  it('canRefund is true only for a CONFIRMED booking with no refund yet', async () => {
    await createComponent(detailWith('CONFIRMED'));
    expect(component.canRefund()).toBe(true);
  });

  it('canRefund is false for an INITIATED booking', async () => {
    await createComponent(detailWith('INITIATED'));
    expect(component.canRefund()).toBe(false);
  });

  it('openCancellationWizard shows the wizard only when canRefund is true', async () => {
    await createComponent(detailWith('CONFIRMED'));

    component.openCancellationWizard();

    expect(component.showCancellationWizard()).toBe(true);
  });

  it('openCancellationWizard is a no-op when the booking cannot be refunded', async () => {
    await createComponent(detailWith('INITIATED'));

    component.openCancellationWizard();

    expect(component.showCancellationWizard()).toBe(false);
  });

  it('onCancelled records the refund, updates status to CANCELLED, and closes the wizard', async () => {
    await createComponent(detailWith('CONFIRMED'));
    const refund: RefundResponse = {
      id: 1,
      paymentId: 1,
      amount: 20,
      policyCode: 'FULL_REFUND',
      status: 'PENDING',
      processedByUserId: null,
      processedAt: null,
    };
    component.openCancellationWizard();

    component.onCancelled(refund);

    expect(component.refund()).toEqual(refund);
    expect(component.detail()?.booking.status).toBe('CANCELLED');
    expect(component.canRefund()).toBe(false);
    expect(component.showCancellationWizard()).toBe(false);
  });

  it('renders the cancellation wizard once opened', async () => {
    await createComponent(detailWith('CONFIRMED'));

    component.openCancellationWizard();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('tw-cancellation-wizard')).not.toBeNull();
  });

  it('canReschedule is true for an INITIATED booking', async () => {
    await createComponent(detailWith('INITIATED'));
    expect(component.canReschedule()).toBe(true);
  });

  it('canReschedule is true for a CONFIRMED booking with no refund yet', async () => {
    await createComponent(detailWith('CONFIRMED'));
    expect(component.canReschedule()).toBe(true);
  });

  it('canReschedule is false for a CANCELLED booking', async () => {
    await createComponent(detailWith('CANCELLED'));
    expect(component.canReschedule()).toBe(false);
  });

  it('startReschedule records the booking id, passenger ids, and requiresFareSettlement=false for an INITIATED booking', async () => {
    await createComponent(detailWith('INITIATED'));

    component.startReschedule();

    expect(rescheduleContext.context()).toEqual({ bookingId: 500, passengerIds: [100], requiresFareSettlement: false });
    expect(router.navigate).toHaveBeenCalledWith(['/search']);
  });

  it('startReschedule records requiresFareSettlement=true for a CONFIRMED booking', async () => {
    await createComponent(detailWith('CONFIRMED'));

    component.startReschedule();

    expect(rescheduleContext.context()).toEqual({ bookingId: 500, passengerIds: [100], requiresFareSettlement: true });
  });

  it('the "Change schedule" button is rendered for both INITIATED and CONFIRMED bookings', async () => {
    await createComponent(detailWith('CONFIRMED'));

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Change schedule');
  });

  it('renders the e-ticket card for a CONFIRMED booking', async () => {
    await createComponent(detailWith('CONFIRMED'));

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('tw-e-ticket-card')).not.toBeNull();
  });

  it('renders the plain items table (not the e-ticket card) for an INITIATED booking', async () => {
    await createComponent(detailWith('INITIATED'));

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('tw-e-ticket-card')).toBeNull();
    expect(root.querySelector('.item-table')).not.toBeNull();
  });
});
