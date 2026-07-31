import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { SeatResponse } from '../../core/models/catalog.model';
import { PassengerResponse } from '../../core/models/passenger.model';
import { RefundResponse } from '../../core/models/payment.model';
import { BookingService } from '../../core/services/booking.service';
import { PassengerService } from '../../core/services/passenger.service';
import { RefundService } from '../../core/services/refund.service';
import { RescheduleContextService } from '../../core/services/reschedule-context.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { BookingDetailsComponent } from './booking-details.component';

describe('BookingDetailsComponent', () => {
  let fixture: ComponentFixture<BookingDetailsComponent>;
  let component: BookingDetailsComponent;
  let bookingService: BookingService;
  let scheduleService: ScheduleService;
  let passengerService: PassengerService;
  let refundService: RefundService;
  let rescheduleContext: RescheduleContextService;
  let router: Router;

  const seat: SeatResponse = {
    id: 1,
    scheduleId: 1,
    seatNumber: '1A',
    seatClass: 'economy',
    status: 'BOOKED',
    priceModifier: 1,
  };

  const passenger: PassengerResponse = {
    id: 100,
    userId: 1,
    fullName: 'Alice Traveler',
    dob: '1990-01-01',
    idType: 'PASSPORT',
    idNumber: 'X123',
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

    passengerService = TestBed.inject(PassengerService);
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([passenger]));

    refundService = TestBed.inject(RefundService);

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

  it('requestRefund initiates a refund and updates status to CANCELLED', async () => {
    await createComponent(detailWith('CONFIRMED'));
    const refund: RefundResponse = {
      id: 1,
      paymentId: 1,
      amount: 20,
      policyCode: 'FULL',
      status: 'PENDING',
      processedByUserId: null,
      processedAt: null,
    };
    vi.spyOn(refundService, 'initiateRefund').mockReturnValue(of(refund));

    component.requestRefund();

    expect(component.refund()).toEqual(refund);
    expect(component.detail()?.booking.status).toBe('CANCELLED');
    expect(component.canRefund()).toBe(false);
  });

  it('canReschedule is true only for an INITIATED booking', async () => {
    await createComponent(detailWith('INITIATED'));
    expect(component.canReschedule()).toBe(true);
  });

  it('canReschedule is false for a CONFIRMED booking', async () => {
    await createComponent(detailWith('CONFIRMED'));
    expect(component.canReschedule()).toBe(false);
  });

  it('startReschedule records the booking id and passenger ids, then navigates to /search', async () => {
    await createComponent(detailWith('INITIATED'));

    component.startReschedule();

    expect(rescheduleContext.context()).toEqual({ bookingId: 500, passengerIds: [100] });
    expect(router.navigate).toHaveBeenCalledWith(['/search']);
  });

  it('the "Change schedule" button is only rendered for an INITIATED booking', async () => {
    await createComponent(detailWith('CONFIRMED'));

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).not.toContain('Change schedule');
  });
});
