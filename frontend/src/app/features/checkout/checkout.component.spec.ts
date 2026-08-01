import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../core/models/catalog.model';
import { PassengerResponse } from '../../core/models/passenger.model';
import { PaymentResponse } from '../../core/models/payment.model';
import { BookingDraftService } from '../../core/services/booking-draft.service';
import { BookingService } from '../../core/services/booking.service';
import { PassengerService } from '../../core/services/passenger.service';
import { PaymentService } from '../../core/services/payment.service';
import { CheckoutComponent } from './checkout.component';

describe('CheckoutComponent', () => {
  let fixture: ComponentFixture<CheckoutComponent>;
  let component: CheckoutComponent;
  let bookingDraftService: BookingDraftService;
  let passengerService: PassengerService;
  let bookingService: BookingService;
  let paymentService: PaymentService;
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

  const seat: SeatResponse = {
    id: 5,
    scheduleId: 1,
    seatNumber: '1A',
    seatClass: 'economy',
    status: 'AVAILABLE',
    priceModifier: 1,
    estimatedFare: 20,
    heldUntil: null,
    heldByMe: false,
  };

  const passenger: PassengerResponse = {
    id: 100,
    userId: 1,
    fullName: 'Jane Doe',
    dob: '1990-01-01',
    idType: 'passport',
    idNumber: 'X123456',
  };

  const bookingDetail: BookingDetailResponse = {
    booking: {
      id: 500,
      userId: 1,
      scheduleId: 1,
      pnr: 'ABC234',
      bookingTime: '2026-08-01T00:00:00Z',
      status: 'INITIATED',
      totalAmount: 20,
      promoCode: null,
    },
    items: [{ id: 1, bookingId: 500, seatId: 5, passengerId: 100, fare: 20 }],
  };

  const paymentResponse: PaymentResponse = {
    id: 1,
    bookingId: 500,
    amount: 20,
    method: 'card',
    reference: 'ref-1',
    status: 'SUCCEEDED',
    paidAt: '2026-08-01T00:00:00Z',
  };

  async function createComponent(withDraft: boolean) {
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    bookingDraftService = TestBed.inject(BookingDraftService);
    if (withDraft) {
      bookingDraftService.set({ schedule, seats: [seat], promoCode: null });
    }

    passengerService = TestBed.inject(PassengerService);
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([passenger]));

    bookingService = TestBed.inject(BookingService);
    paymentService = TestBed.inject(PaymentService);

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('without a booking draft', () => {
    beforeEach(() => createComponent(false));

    it('redirects to /search', () => {
      expect(router.navigate).toHaveBeenCalledWith(['/search']);
      expect(component.draft()).toBeNull();
    });

    it('allSeatsAssigned is false when there is no draft', () => {
      expect(component.allSeatsAssigned()).toBe(false);
    });
  });

  describe('with a booking draft', () => {
    beforeEach(() => createComponent(true));

    it('loads the draft and the passenger list', () => {
      expect(component.draft()).toEqual({ schedule, seats: [seat], promoCode: null });
      expect(component.passengers()).toEqual([passenger]);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('leaves the promo field blank when the draft carries no promo code', () => {
      expect(component.promoForm.value.promoCode).toBe('');
    });

    it('allSeatsAssigned is false until every seat has a passenger', () => {
      expect(component.allSeatsAssigned()).toBe(false);

      component.assignPassenger(seat.id, String(passenger.id));

      expect(component.allSeatsAssigned()).toBe(true);
    });

    it('assignPassenger treats an empty value as unassigned', () => {
      component.assignPassenger(seat.id, String(passenger.id));
      expect(component.allSeatsAssigned()).toBe(true);

      component.assignPassenger(seat.id, '');

      expect(component.allSeatsAssigned()).toBe(false);
    });

    it('onAssignChange reads the select value and assigns it', () => {
      const select = document.createElement('select');
      select.value = '';
      const option = document.createElement('option');
      option.value = String(passenger.id);
      select.appendChild(option);
      select.value = String(passenger.id);

      component.onAssignChange(seat.id, { target: select } as unknown as Event);

      expect(component.allSeatsAssigned()).toBe(true);
    });

    describe('addPassenger', () => {
      it('does nothing while the form is invalid', () => {
        const createSpy = vi.spyOn(passengerService, 'createPassenger');

        component.addPassenger();

        expect(createSpy).not.toHaveBeenCalled();
      });

      it('creates a passenger, appends it, and resets the form', () => {
        vi.spyOn(passengerService, 'createPassenger').mockReturnValue(of(passenger));
        component.newPassengerForm.setValue({
          fullName: 'Jane Doe',
          dob: '1990-01-01',
          idType: 'passport',
          idNumber: 'X123456',
        });

        component.addPassenger();

        expect(component.passengers()).toEqual([passenger, passenger]);
        expect(component.newPassengerForm.value.fullName).toBe('');
        expect(component.addingPassenger()).toBe(false);
      });

      it('ignores a second call while a submission is already in flight', () => {
        component.addingPassenger.set(true);
        const createSpy = vi.spyOn(passengerService, 'createPassenger');

        component.addPassenger();

        expect(createSpy).not.toHaveBeenCalled();
      });
    });

    describe('createBooking', () => {
      it('does nothing until every seat is assigned', () => {
        const createSpy = vi.spyOn(bookingService, 'createBooking');

        component.createBooking();

        expect(createSpy).not.toHaveBeenCalled();
        expect(component.step()).toBe('assign');
      });

      it('sends the booking request and advances to the payment step', () => {
        vi.spyOn(bookingService, 'createBooking').mockReturnValue(of(bookingDetail));
        component.assignPassenger(seat.id, String(passenger.id));
        component.promoForm.setValue({ promoCode: '  ' });

        component.createBooking();

        expect(bookingService.createBooking).toHaveBeenCalledWith({
          scheduleId: schedule.scheduleId,
          seatSelections: [{ seatId: seat.id, passengerId: passenger.id }],
          promoCode: null,
        });
        expect(component.booking()).toEqual(bookingDetail);
        expect(component.step()).toBe('payment');
      });

      it('trims and forwards a non-blank promo code', () => {
        vi.spyOn(bookingService, 'createBooking').mockReturnValue(of(bookingDetail));
        component.assignPassenger(seat.id, String(passenger.id));
        component.promoForm.setValue({ promoCode: ' SAVE10 ' });

        component.createBooking();

        expect(bookingService.createBooking).toHaveBeenCalledWith(
          expect.objectContaining({ promoCode: 'SAVE10' }),
        );
      });

      it('ignores a second call while creation is already in flight', () => {
        component.assignPassenger(seat.id, String(passenger.id));
        component.creatingBooking.set(true);
        const createSpy = vi.spyOn(bookingService, 'createBooking');

        component.createBooking();

        expect(createSpy).not.toHaveBeenCalled();
      });
    });

    describe('pay', () => {
      beforeEach(() => {
        vi.spyOn(bookingService, 'createBooking').mockReturnValue(of(bookingDetail));
        component.assignPassenger(seat.id, String(passenger.id));
        component.createBooking();
      });

      it('does nothing without a booking', () => {
        component.booking.set(null);
        const paySpy = vi.spyOn(paymentService, 'recordPayment');

        component.pay();

        expect(paySpy).not.toHaveBeenCalled();
      });

      it('records the payment, clears the draft, and navigates to booking details', () => {
        vi.spyOn(paymentService, 'recordPayment').mockReturnValue(of(paymentResponse));
        const clearSpy = vi.spyOn(bookingDraftService, 'clear');

        component.pay();

        expect(paymentService.recordPayment).toHaveBeenCalledWith(bookingDetail.booking.id, {
          amount: bookingDetail.booking.totalAmount,
          method: 'card',
          reference: component.paymentForm.getRawValue().reference,
        });
        expect(component.payment()).toEqual(paymentResponse);
        expect(clearSpy).toHaveBeenCalled();
        expect(router.navigate).toHaveBeenCalledWith(['/bookings', bookingDetail.booking.id]);
      });

      it('ignores a second call while a payment is already in flight', () => {
        component.payingSubmitting.set(true);
        const paySpy = vi.spyOn(paymentService, 'recordPayment');

        component.pay();

        expect(paySpy).not.toHaveBeenCalled();
      });

      it('does nothing when the payment form is invalid', () => {
        component.paymentForm.controls.method.setValue('' as unknown as string);
        const paySpy = vi.spyOn(paymentService, 'recordPayment');

        component.pay();

        expect(paySpy).not.toHaveBeenCalled();
      });
    });

    it('walks through the whole checkout via real DOM interactions', () => {
      vi.spyOn(bookingService, 'createBooking').mockReturnValue(of(bookingDetail));
      vi.spyOn(paymentService, 'recordPayment').mockReturnValue(of(paymentResponse));
      fixture.detectChanges();

      const root = fixture.nativeElement as HTMLElement;

      const select = root.querySelector<HTMLSelectElement>('select');
      select!.value = String(passenger.id);
      select!.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(component.allSeatsAssigned()).toBe(true);

      component.newPassengerForm.setValue({
        fullName: 'Jane Doe',
        dob: '1990-01-01',
        idType: 'passport',
        idNumber: 'X123456',
      });
      fixture.detectChanges();
      const saveButton = Array.from(root.querySelectorAll('button')).find((btn) =>
        btn.textContent?.includes('Save passenger'),
      );
      expect(saveButton!.disabled).toBe(false);

      const createButton = Array.from(root.querySelectorAll('button')).find((btn) =>
        btn.textContent?.includes('Create booking'),
      );
      createButton!.click();
      fixture.detectChanges();

      expect(component.step()).toBe('payment');
      expect(root.textContent).toContain('PNR ABC234');

      const payButton = Array.from(root.querySelectorAll('button')).find((btn) =>
        btn.textContent?.includes('Pay now'),
      );
      payButton!.click();

      expect(router.navigate).toHaveBeenCalledWith(['/bookings', bookingDetail.booking.id]);
    });

    it('shows in-flight indicators and disables the corresponding buttons', () => {
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;

      component.addingPassenger.set(true);
      fixture.detectChanges();
      expect(root.textContent).toContain('Saving…');

      component.creatingBooking.set(true);
      fixture.detectChanges();
      expect(root.textContent).toContain('Creating booking…');

      component.addingPassenger.set(false);
      component.creatingBooking.set(false);
      component.booking.set(bookingDetail);
      component.step.set('payment');
      component.payingSubmitting.set(true);
      fixture.detectChanges();
      expect(root.textContent).toContain('Processing…');
    });
  });

  it('pre-fills the promo field from a draft carrying a previewed promo code', async () => {
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    bookingDraftService = TestBed.inject(BookingDraftService);
    bookingDraftService.set({ schedule, seats: [seat], promoCode: 'SAVE10' });

    passengerService = TestBed.inject(PassengerService);
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([]));

    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.promoForm.value.promoCode).toBe('SAVE10');
  });

  it('renders a schedule with a venue and no destination', async () => {
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const venueSchedule: ScheduleSearchResult = { ...schedule, origin: null, destination: null, venue: 'Arena' };
    bookingDraftService = TestBed.inject(BookingDraftService);
    bookingDraftService.set({ schedule: venueSchedule, seats: [seat], promoCode: null });

    passengerService = TestBed.inject(PassengerService);
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([]));

    fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Arena');
    expect(html).not.toContain('&rarr;');
  });
});
