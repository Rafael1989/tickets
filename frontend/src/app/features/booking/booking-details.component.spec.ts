import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../core/models/catalog.model';
import { PassengerResponse } from '../../core/models/passenger.model';
import { RefundQuoteResponse, RefundResponse } from '../../core/models/payment.model';
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

  function refundQuoteWith(eligible: boolean): RefundQuoteResponse {
    return {
      bookingId: 500,
      fareAmount: 20,
      policyCode: eligible ? 'FULL_REFUND' : null,
      refundRate: eligible ? 1 : null,
      refundAmount: eligible ? 20 : 0,
      nonRefundableAmount: eligible ? 0 : 20,
      paymentMethod: 'card',
      eligible,
    };
  }

  async function createComponent(
    detail: BookingDetailResponse | 'error',
    existingRefunds: RefundResponse[] = [],
    refundQuote: RefundQuoteResponse = refundQuoteWith(true),
  ) {
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

    refundService = TestBed.inject(RefundService);
    vi.spyOn(refundService, 'listRefundsForBooking').mockReturnValue(of(existingRefunds));
    vi.spyOn(refundService, 'getRefundQuote').mockReturnValue(of(refundQuote));

    rescheduleContext = TestBed.inject(RescheduleContextService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(BookingDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function refundWith(status: 'PENDING' | 'PROCESSED' | 'REJECTED'): RefundResponse {
    return {
      id: 1,
      paymentId: 1,
      amount: 20,
      policyCode: 'FULL_REFUND',
      status,
      processedByUserId: null,
      processedAt: null,
      overrideDelta: null,
      overrideReason: null,
    };
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

  it('onCancelled records the refund and closes the wizard, leaving the booking CONFIRMED pending review', async () => {
    await createComponent(detailWith('CONFIRMED'));
    const refund = refundWith('PENDING');
    component.openCancellationWizard();

    component.onCancelled(refund);

    expect(component.refund()).toEqual(refund);
    // The backend keeps the booking CONFIRMED until support approves, so the UI must not claim
    // it's cancelled — a rejection would leave that optimistic status flatly wrong.
    expect(component.detail()?.booking.status).toBe('CONFIRMED');
    expect(component.canRefund()).toBe(false);
    expect(component.showCancellationWizard()).toBe(false);
  });

  it('loads an existing refund from a prior session and reflects it in canRefund', async () => {
    await createComponent(detailWith('CONFIRMED'), [refundWith('PENDING')]);

    expect(component.refund()).toEqual(refundWith('PENDING'));
    expect(component.canRefund()).toBe(false);
  });

  it('picks the newest refund when more than one is returned', async () => {
    const newest = refundWith('PROCESSED');
    await createComponent(detailWith('CONFIRMED'), [newest, refundWith('REJECTED')]);

    expect(component.refund()).toEqual(newest);
  });

  it('polls for a PENDING refund and stops once support resolves it', async () => {
    vi.useFakeTimers();
    await createComponent(detailWith('CONFIRMED'), [refundWith('PENDING')]);
    const resolved = refundWith('PROCESSED');
    vi.spyOn(refundService, 'listRefundsForBooking').mockReturnValue(of([resolved]));

    vi.advanceTimersByTime(10_000);

    expect(component.refund()).toEqual(resolved);
    expect(component.canRefund()).toBe(false);

    const callsAfterResolution = (refundService.listRefundsForBooking as ReturnType<typeof vi.spyOn>).mock.calls
      .length;
    vi.advanceTimersByTime(30_000);

    expect((refundService.listRefundsForBooking as ReturnType<typeof vi.spyOn>).mock.calls.length).toBe(
      callsAfterResolution,
    );
    vi.useRealTimers();
  });

  it('does not poll when there is no refund yet', async () => {
    vi.useFakeTimers();
    await createComponent(detailWith('CONFIRMED'), []);
    const spy = vi.spyOn(refundService, 'listRefundsForBooking');
    spy.mockClear();

    vi.advanceTimersByTime(30_000);

    expect(spy).not.toHaveBeenCalled();
    vi.useRealTimers();
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

  it('canReschedule is false for a CONFIRMED booking too close to departure, and the reason is shown instead of the button', async () => {
    await createComponent(detailWith('CONFIRMED'), [], refundQuoteWith(false));

    expect(component.canReschedule()).toBe(false);
    expect(component.rescheduleTooCloseToDeparture()).toBe(true);

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('too close to departure to reschedule online');
    expect(html).not.toContain('Change schedule');
  });

  it('never fetches a refund quote for an INITIATED booking, which is always reschedulable', async () => {
    await createComponent(detailWith('INITIATED'));

    expect(refundService.getRefundQuote).not.toHaveBeenCalled();
    expect(component.canReschedule()).toBe(true);
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
