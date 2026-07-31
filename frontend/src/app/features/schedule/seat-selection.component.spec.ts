import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject, of } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../core/models/catalog.model';
import { AuthService } from '../../core/services/auth.service';
import { BookingDraftService } from '../../core/services/booking-draft.service';
import { BookingService } from '../../core/services/booking.service';
import { RescheduleContextService } from '../../core/services/reschedule-context.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { SeatSelectionComponent } from './seat-selection.component';

describe('SeatSelectionComponent', () => {
  let fixture: ComponentFixture<SeatSelectionComponent>;
  let component: SeatSelectionComponent;
  let scheduleService: ScheduleService;
  let bookingDraft: BookingDraftService;
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
  };
  const heldSeat: SeatResponse = {
    id: 2,
    scheduleId: 1,
    seatNumber: '1B',
    seatClass: 'economy',
    status: 'HELD',
    priceModifier: 1.5,
  };

  async function createComponent() {
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
    vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of([availableSeat, heldSeat]));

    bookingDraft = TestBed.inject(BookingDraftService);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(SeatSelectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    localStorage.clear();
    await createComponent();
  });

  afterEach(() => localStorage.clear());

  it('loads the schedule and seats', () => {
    expect(component.loading()).toBe(false);
    expect(component.schedule()).toEqual(schedule);
    expect(component.seats()).toEqual([availableSeat, heldSeat]);
  });

  it('toggleSeat selects and deselects an available seat', () => {
    component.toggleSeat(availableSeat);
    expect(component.selectedSeats()).toEqual([availableSeat]);

    component.toggleSeat(availableSeat);
    expect(component.selectedSeats()).toEqual([]);
  });

  it('toggleSeat ignores a seat that is not available', () => {
    component.toggleSeat(heldSeat);

    expect(component.selectedSeats()).toEqual([]);
  });

  it('estimatedTotal sums baseFare * priceModifier for selected seats', () => {
    component.toggleSeat(availableSeat);

    expect(component.estimatedTotal()).toBe(20);
  });

  it('continueToCheckout does nothing when no seats are selected', () => {
    const setSpy = vi.spyOn(bookingDraft, 'set');

    component.continueToCheckout();

    expect(setSpy).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('continueToCheckout navigates to /checkout when authenticated', () => {
    vi.spyOn(auth, 'isAuthenticated').mockReturnValue(true);
    const setSpy = vi.spyOn(bookingDraft, 'set');
    component.toggleSeat(availableSeat);

    component.continueToCheckout();

    expect(setSpy).toHaveBeenCalledWith({ schedule, seats: [availableSeat] });
    expect(router.navigate).toHaveBeenCalledWith(['/checkout']);
  });

  it('continueToCheckout redirects to login when not authenticated', () => {
    vi.spyOn(auth, 'isAuthenticated').mockReturnValue(false);
    component.toggleSeat(availableSeat);

    component.continueToCheckout();

    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { redirectTo: '/checkout' } });
  });

  it('selecting a seat and continuing works via real DOM clicks', () => {
    vi.spyOn(auth, 'isAuthenticated').mockReturnValue(true);
    fixture.detectChanges();

    const seatButton = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      'button.seat:not(.unavailable)',
    );
    seatButton!.click();
    fixture.detectChanges();

    expect(component.selectedSeats()).toEqual([availableSeat]);
    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('seat(s) selected');

    const continueButton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((btn) => btn.textContent?.includes('Continue to checkout'));
    continueButton!.click();

    expect(router.navigate).toHaveBeenCalledWith(['/checkout']);
  });

  it('renders a schedule with a venue and no destination', async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
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
    expect(html).not.toContain('&rarr;');
  });

  describe('while the schedule/seats request is still pending', () => {
    let scheduleSubject: Subject<ScheduleSearchResult>;
    let seatsSubject: Subject<SeatResponse[]>;

    beforeEach(async () => {
      localStorage.clear();
      TestBed.resetTestingModule();
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

      scheduleSubject = new Subject();
      seatsSubject = new Subject();
      scheduleService = TestBed.inject(ScheduleService);
      vi.spyOn(scheduleService, 'getSchedule').mockReturnValue(scheduleSubject);
      vi.spyOn(scheduleService, 'getSeats').mockReturnValue(seatsSubject);

      fixture = TestBed.createComponent(SeatSelectionComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('shows a loading message and a zero estimated total', () => {
      expect(component.loading()).toBe(true);
      expect(component.estimatedTotal()).toBe(0);

      const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(html).toContain('Loading schedule');

      scheduleSubject.next(schedule);
      scheduleSubject.complete();
      seatsSubject.next([availableSeat]);
      seatsSubject.complete();
      fixture.detectChanges();

      expect(component.loading()).toBe(false);
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

    async function createInRescheduleMode(passengerIds: number[]) {
      localStorage.clear();
      TestBed.resetTestingModule();
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
      vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of([availableSeat, heldSeat]));

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
      component.toggleSeat(availableSeat);

      component.continueToCheckout();

      expect(bookingService.rescheduleBooking).not.toHaveBeenCalled();
    });

    it('continueToCheckout reschedules the booking and navigates to it on success', async () => {
      await createInRescheduleMode([100]);
      component.toggleSeat(availableSeat);

      component.continueToCheckout();

      expect(bookingService.rescheduleBooking).toHaveBeenCalledWith(500, {
        scheduleId: schedule.scheduleId,
        seatSelections: [{ seatId: availableSeat.id, passengerId: 100 }],
      });
      expect(rescheduleContext.context()).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/bookings', 500]);
    });

    it('does not touch the booking draft or checkout route while rescheduling', async () => {
      await createInRescheduleMode([100]);
      const bookingDraft = TestBed.inject(BookingDraftService);
      const setSpy = vi.spyOn(bookingDraft, 'set');
      component.toggleSeat(availableSeat);

      component.continueToCheckout();

      expect(setSpy).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalledWith(['/checkout']);
    });
  });
});
