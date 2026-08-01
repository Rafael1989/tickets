import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../core/models/catalog.model';
import { PromoValidationResponse } from '../../core/models/promo.model';
import { AuthService } from '../../core/services/auth.service';
import { BookingDraftService } from '../../core/services/booking-draft.service';
import { BookingService } from '../../core/services/booking.service';
import { NotificationService } from '../../core/services/notification.service';
import { PromoService } from '../../core/services/promo.service';
import { RescheduleContextService } from '../../core/services/reschedule-context.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { SeatSelectionComponent } from './seat-selection.component';

function fakeJwt(sub: string, roles: string[] = ['CUSTOMER']): string {
  const base64url = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${base64url({ alg: 'HS512' })}.${base64url({ sub, roles, iat: 1, exp: 9999999999 })}.sig`;
}

describe('SeatSelectionComponent', () => {
  let fixture: ComponentFixture<SeatSelectionComponent>;
  let component: SeatSelectionComponent;
  let scheduleService: ScheduleService;
  let bookingDraft: BookingDraftService;
  let promoService: PromoService;
  let notifications: NotificationService;
  let auth: AuthService;
  let router: Router;

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

  const availableSeat: SeatResponse = {
    id: 1,
    scheduleId: 1,
    seatNumber: '1A',
    seatClass: 'economy',
    status: 'AVAILABLE',
    priceModifier: 1,
    estimatedFare: 20,
    heldUntil: null,
    heldByMe: false,
  };
  const heldByOtherSeat: SeatResponse = {
    id: 2,
    scheduleId: 1,
    seatNumber: '1B',
    seatClass: 'business',
    status: 'HELD',
    priceModifier: 1.5,
    estimatedFare: 30,
    heldUntil: '2026-08-01T00:10:00Z',
    heldByMe: false,
  };

  // isAuthenticated is a real Angular signal the component aliases directly
  // (for correct production reactivity) — vi.spyOn after construction can't
  // retroactively rewrite that alias, so tests establish real auth state via
  // a token in localStorage *before* the component is constructed, exactly
  // like NavbarComponent's spec does.
  async function createComponent(authenticated = false): Promise<void> {
    if (authenticated) {
      localStorage.setItem('tw.accessToken', fakeJwt('alice'));
    }

    await TestBed.configureTestingModule({
      imports: [SeatSelectionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } },
        },
      ],
    }).compileComponents();

    scheduleService = TestBed.inject(ScheduleService);
    vi.spyOn(scheduleService, 'getSchedule').mockReturnValue(of(schedule));
    vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of([availableSeat, heldByOtherSeat]));

    bookingDraft = TestBed.inject(BookingDraftService);
    promoService = TestBed.inject(PromoService);
    notifications = TestBed.inject(NotificationService);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(SeatSelectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => localStorage.clear());

  describe('loading', () => {
    beforeEach(async () => {
      localStorage.clear();
      await createComponent();
    });

    it('loads the schedule and seats', () => {
      expect(component.loading()).toBe(false);
      expect(component.schedule()).toEqual(schedule);
      expect(component.seats()).toEqual([availableSeat, heldByOtherSeat]);
    });
  });

  describe('as a guest', () => {
    beforeEach(async () => {
      localStorage.clear();
      await createComponent(false);
    });

    it('toggleSeat selects and deselects an available seat locally, without holding it server-side', () => {
      const holdSpy = vi.spyOn(scheduleService, 'holdSeat');

      component.toggleSeat(availableSeat);
      expect(component.selectedSeats()).toEqual([availableSeat]);

      component.toggleSeat(availableSeat);
      expect(component.selectedSeats()).toEqual([]);
      expect(holdSpy).not.toHaveBeenCalled();
    });

    it('toggleSeat ignores a seat that is not available', () => {
      component.toggleSeat(heldByOtherSeat);

      expect(component.selectedSeats()).toEqual([]);
    });

    it('continueToCheckout redirects to login when not authenticated', () => {
      component.toggleSeat(availableSeat);

      component.continueToCheckout();

      expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { redirectTo: '/checkout' } });
    });
  });

  describe('as an authenticated customer', () => {
    beforeEach(async () => {
      localStorage.clear();
      await createComponent(true);
    });

    it('toggleSeat holds an available seat server-side and reflects it as selected once held', () => {
      const held: SeatResponse = { ...availableSeat, status: 'HELD', heldByMe: true, heldUntil: '2026-08-01T00:10:00Z' };
      vi.spyOn(scheduleService, 'holdSeat').mockReturnValue(of(held));

      component.toggleSeat(availableSeat);

      expect(scheduleService.holdSeat).toHaveBeenCalledWith(1, availableSeat.id);
      expect(component.selectedSeats()).toEqual([held]);
    });

    it('toggleSeat releases a seat the caller already holds', () => {
      const held: SeatResponse = { ...availableSeat, status: 'HELD', heldByMe: true, heldUntil: '2026-08-01T00:10:00Z' };
      vi.spyOn(scheduleService, 'holdSeat').mockReturnValue(of(held));
      vi.spyOn(scheduleService, 'releaseSeat').mockReturnValue(of(undefined));
      component.toggleSeat(availableSeat);
      expect(component.selectedSeats()).toHaveLength(1);

      component.toggleSeat(held);

      expect(scheduleService.releaseSeat).toHaveBeenCalledWith(1, availableSeat.id);
      expect(component.selectedSeats()).toEqual([]);
    });

    it('shows a conflict toast and refreshes the seat map when a hold attempt 409s', () => {
      vi.spyOn(scheduleService, 'holdSeat').mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 409 })),
      );
      const refreshedSeats = [{ ...availableSeat, status: 'HELD' as const }, heldByOtherSeat];
      const getSeatsSpy = vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of(refreshedSeats));
      const errorSpy = vi.spyOn(notifications, 'error');

      component.toggleSeat(availableSeat);

      expect(errorSpy).toHaveBeenCalledWith(expect.stringContaining('just taken by another user'));
      expect(getSeatsSpy).toHaveBeenCalled();
      expect(component.seats()).toEqual(refreshedSeats);
    });

    it('continueToCheckout sets the draft and navigates straight to checkout', () => {
      const held: SeatResponse = { ...availableSeat, status: 'HELD', heldByMe: true };
      vi.spyOn(scheduleService, 'holdSeat').mockReturnValue(of(held));
      const setSpy = vi.spyOn(bookingDraft, 'set');
      component.toggleSeat(availableSeat);

      component.continueToCheckout();

      expect(setSpy).toHaveBeenCalledWith({ schedule, seats: [held], promoCode: null });
      expect(router.navigate).toHaveBeenCalledWith(['/checkout']);
    });
  });

  describe('pricing', () => {
    beforeEach(async () => {
      localStorage.clear();
      await createComponent(false);
    });

    it('subtotal sums estimatedFare across selected seats', () => {
      component.toggleSeat(availableSeat);

      expect(component.subtotal()).toBe(20);
    });

    it('total equals subtotal when no promo is applied', () => {
      component.toggleSeat(availableSeat);

      expect(component.total()).toBe(20);
    });

    it('total subtracts the applied promo discount', () => {
      component.toggleSeat(availableSeat);
      component.appliedPromo.set({ code: 'SAVE5', discountAmount: 5, totalAfterDiscount: 15 });

      expect(component.total()).toBe(15);
    });
  });

  describe('promo validation', () => {
    beforeEach(async () => {
      localStorage.clear();
      await createComponent(false);
      component.toggleSeat(availableSeat);
    });

    it('validatePromo applies a valid code', () => {
      const response: PromoValidationResponse = { code: 'SAVE5', discountAmount: 5, totalAfterDiscount: 15 };
      vi.spyOn(promoService, 'validate').mockReturnValue(of(response));
      component.promoForm.setValue({ promoCode: 'SAVE5' });

      component.validatePromo();

      expect(promoService.validate).toHaveBeenCalledWith({ code: 'SAVE5', subtotal: 20 });
      expect(component.appliedPromo()).toEqual(response);
      expect(component.promoLoading()).toBe(false);
    });

    it('validatePromo surfaces the backend error message on failure', () => {
      vi.spyOn(promoService, 'validate').mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 404, error: { message: 'No such promo code' } })),
      );
      component.promoForm.setValue({ promoCode: 'NOPE' });

      component.validatePromo();

      expect(component.appliedPromo()).toBeNull();
      expect(component.promoError()).toBe('No such promo code');
    });

    it('validatePromo does nothing for a blank code', () => {
      const validateSpy = vi.spyOn(promoService, 'validate');
      component.promoForm.setValue({ promoCode: '   ' });

      component.validatePromo();

      expect(validateSpy).not.toHaveBeenCalled();
    });

    it('clearPromo resets the applied promo and form', () => {
      component.appliedPromo.set({ code: 'SAVE5', discountAmount: 5, totalAfterDiscount: 15 });
      component.promoForm.setValue({ promoCode: 'SAVE5' });

      component.clearPromo();

      expect(component.appliedPromo()).toBeNull();
      expect(component.promoForm.value.promoCode).toBe('');
    });
  });

  describe('countdown', () => {
    beforeEach(async () => {
      localStorage.clear();
      await createComponent(false);
    });

    it('remainingSeconds is 0 for a seat with no heldUntil', () => {
      expect(component.remainingSeconds(availableSeat)).toBe(0);
    });

    it('remainingSeconds counts down to the heldUntil instant', () => {
      const future = new Date(Date.now() + 90_000).toISOString();
      const seat: SeatResponse = { ...availableSeat, heldUntil: future };

      const remaining = component.remainingSeconds(seat);

      expect(remaining).toBeGreaterThan(85);
      expect(remaining).toBeLessThanOrEqual(90);
    });

    it('formatCountdown renders minutes:seconds', () => {
      const future = new Date(Date.now() + 65_000).toISOString();
      const seat: SeatResponse = { ...availableSeat, heldUntil: future };

      expect(component.formatCountdown(seat)).toMatch(/^1:0[0-9]$/);
    });
  });

  describe('reschedule mode', () => {
    let bookingService: BookingService;
    let rescheduleContext: RescheduleContextService;

    const detail: BookingDetailResponse = {
      booking: {
        id: 500,
        userId: 1,
        scheduleId: 1,
        pnr: 'ABC123',
        bookingTime: '2026-01-01T00:00:00Z',
        status: 'INITIATED',
        totalAmount: 20,
        promoCode: null,
      },
      items: [],
    };

    async function createInRescheduleMode(passengerIds: number[]): Promise<void> {
      localStorage.clear();
      localStorage.setItem('tw.accessToken', fakeJwt('alice'));
      await TestBed.configureTestingModule({
        imports: [SeatSelectionComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } } },
        ],
      }).compileComponents();

      scheduleService = TestBed.inject(ScheduleService);
      vi.spyOn(scheduleService, 'getSchedule').mockReturnValue(of(schedule));
      vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of([availableSeat, heldByOtherSeat]));

      bookingService = TestBed.inject(BookingService);
      vi.spyOn(bookingService, 'rescheduleBooking').mockReturnValue(of(detail));

      rescheduleContext = TestBed.inject(RescheduleContextService);
      rescheduleContext.start(500, passengerIds);

      router = TestBed.inject(Router);
      vi.spyOn(router, 'navigate').mockResolvedValue(true);

      fixture = TestBed.createComponent(SeatSelectionComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    }

    afterEach(() => localStorage.clear());

    it('isRescheduleMode is true once a reschedule context is active', async () => {
      await createInRescheduleMode([100]);

      expect(component.isRescheduleMode()).toBe(true);
    });

    it('continueToCheckout rejects a seat count mismatch instead of calling the backend', async () => {
      await createInRescheduleMode([100, 200]);
      const held: SeatResponse = { ...availableSeat, status: 'HELD', heldByMe: true };
      vi.spyOn(scheduleService, 'holdSeat').mockReturnValue(of(held));
      component.toggleSeat(availableSeat);

      component.continueToCheckout();

      expect(bookingService.rescheduleBooking).not.toHaveBeenCalled();
    });

    it('continueToCheckout reschedules the booking and navigates to it on success', async () => {
      await createInRescheduleMode([100]);
      const held: SeatResponse = { ...availableSeat, status: 'HELD', heldByMe: true };
      vi.spyOn(scheduleService, 'holdSeat').mockReturnValue(of(held));
      component.toggleSeat(availableSeat);

      component.continueToCheckout();

      expect(bookingService.rescheduleBooking).toHaveBeenCalledWith(500, {
        scheduleId: schedule.scheduleId,
        seatSelections: [{ seatId: availableSeat.id, passengerId: 100 }],
      });
      expect(rescheduleContext.context()).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/bookings', 500]);
    });
  });

  it('renders a schedule with a venue and no destination', async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [SeatSelectionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } } },
      ],
    }).compileComponents();

    const eventSchedule: ScheduleSearchResult = {
      ...schedule,
      type: 'EVENT',
      origin: null,
      destination: null,
      venue: 'Arena',
    };
    scheduleService = TestBed.inject(ScheduleService);
    vi.spyOn(scheduleService, 'getSchedule').mockReturnValue(of(eventSchedule));
    vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of([availableSeat]));

    fixture = TestBed.createComponent(SeatSelectionComponent);
    fixture.detectChanges();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Arena');
  });
});
