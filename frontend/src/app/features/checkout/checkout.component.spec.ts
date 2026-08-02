import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../core/models/catalog.model';
import { PassengerResponse } from '../../core/models/passenger.model';
import { PaymentResponse } from '../../core/models/payment.model';
import { BookingDraftService } from '../../core/services/booking-draft.service';
import { BookingService } from '../../core/services/booking.service';
import { PassengerService } from '../../core/services/passenger.service';
import { PaymentService } from '../../core/services/payment.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { CheckoutComponent } from './checkout.component';

describe('CheckoutComponent', () => {
  let fixture: ComponentFixture<CheckoutComponent>;
  let component: CheckoutComponent;
  let bookingDraftService: BookingDraftService;
  let passengerService: PassengerService;
  let bookingService: BookingService;
  let paymentService: PaymentService;
  let scheduleService: ScheduleService;
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
    heldUntil: new Date(Date.now() + 10 * 60_000).toISOString(),
    heldByMe: true,
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

  const succeededPayment: PaymentResponse = {
    id: 1,
    bookingId: 500,
    amount: 20,
    method: 'card',
    reference: 'ref-1',
    status: 'SUCCEEDED',
    paidAt: '2026-08-01T00:00:00Z',
    failureReason: null,
  };

  const declinedPayment: PaymentResponse = {
    id: 2,
    bookingId: 500,
    amount: 20,
    method: 'card',
    reference: 'ref-2',
    status: 'FAILED',
    paidAt: null,
    failureReason: 'Your card was declined.',
  };

  const pending3dsPayment: PaymentResponse = {
    id: 3,
    bookingId: 500,
    amount: 20,
    method: 'card',
    reference: 'ref-3',
    status: 'PENDING_3DS',
    paidAt: null,
    failureReason: null,
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

    scheduleService = TestBed.inject(ScheduleService);
    vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of([seat]));

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function fillCardForm(overrides: Partial<{ cardNumber: string }> = {}) {
    component.cardForm.setValue({
      cardholderName: 'Jane Doe',
      cardNumber: overrides.cardNumber ?? '4242 4242 4242 4242',
      expiry: '12/29',
      cvc: '123',
    });
  }

  async function proceedToPaymentStep() {
    vi.spyOn(bookingService, 'createBooking').mockReturnValue(of(bookingDetail));
    component.assignPassenger(seat.id, String(passenger.id));
    component.createBooking();
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

    it('allSeatsAssigned is false until every seat has a passenger', () => {
      expect(component.allSeatsAssigned()).toBe(false);

      component.assignPassenger(seat.id, String(passenger.id));

      expect(component.allSeatsAssigned()).toBe(true);
    });

    it('holdDeadline reflects the earliest heldUntil among the draft seats during the assign step', () => {
      expect(component.holdDeadline()).toBe(seat.heldUntil);
    });

    describe('createBooking', () => {
      it('does nothing until every seat is assigned', () => {
        const createSpy = vi.spyOn(bookingService, 'createBooking');

        component.createBooking();

        expect(createSpy).not.toHaveBeenCalled();
        expect(component.step()).toBe('assign');
      });

      it('sends the booking request, advances to the payment step, and refreshes seat holds', () => {
        proceedToPaymentStep();

        expect(bookingService.createBooking).toHaveBeenCalledWith({
          scheduleId: schedule.scheduleId,
          seatSelections: [{ seatId: seat.id, passengerId: passenger.id }],
          promoCode: null,
        });
        expect(component.booking()).toEqual(bookingDetail);
        expect(component.step()).toBe('payment');
        expect(scheduleService.getSeats).toHaveBeenCalledWith(schedule.scheduleId);
      });
    });

    describe('onHoldExpired', () => {
      it('sets holdExpired unless the payment already succeeded', () => {
        component.onHoldExpired();
        expect(component.holdExpired()).toBe(true);
      });

      it('is ignored once the payment has succeeded', () => {
        component.paymentState.set('succeeded');

        component.onHoldExpired();

        expect(component.holdExpired()).toBe(false);
      });
    });

    describe('addPassenger', () => {
      it('does not submit when the ID number fails the Luhn-style format check for the selected ID type', () => {
        const createSpy = vi.spyOn(passengerService, 'createPassenger');
        component.newPassengerForm.setValue({
          fullName: 'Alex Guest',
          dob: '1990-01-01',
          idType: 'passport',
          idNumber: 'A1',
        });

        component.addPassenger();

        expect(createSpy).not.toHaveBeenCalled();
        expect(component.newPassengerForm.controls.idNumber.hasError('idFormat')).toBe(true);
      });

      it('re-validates the ID number format when the ID type changes', () => {
        component.newPassengerForm.setValue({
          fullName: 'Alex Guest',
          dob: '1990-01-01',
          idType: 'passport',
          idNumber: '123456789012345',
        });
        expect(component.newPassengerForm.controls.idNumber.hasError('idFormat')).toBe(true);

        component.newPassengerForm.controls.idType.setValue('national_id');

        expect(component.newPassengerForm.controls.idNumber.hasError('idFormat')).toBe(false);
      });
    });

    describe('card payment', () => {
      beforeEach(() => {
        vi.useFakeTimers();
        proceedToPaymentStep();
      });

      afterEach(() => vi.useRealTimers());

      it('does nothing while the card form is invalid', () => {
        const paySpy = vi.spyOn(paymentService, 'recordPayment');

        component.payWithCard();

        expect(paySpy).not.toHaveBeenCalled();
        expect(component.paymentState()).toBe('idle');
      });

      it('does nothing when the card number fails the Luhn checksum', () => {
        const paySpy = vi.spyOn(paymentService, 'recordPayment');
        fillCardForm({ cardNumber: '4242 4242 4242 4241' });

        component.payWithCard();

        expect(paySpy).not.toHaveBeenCalled();
        expect(component.cardForm.controls.cardNumber.hasError('luhn')).toBe(true);
      });

      it('goes through a processing state before revealing success', () => {
        vi.spyOn(paymentService, 'recordPayment').mockReturnValue(of(succeededPayment));
        fillCardForm();

        component.payWithCard();
        expect(component.paymentState()).toBe('processing');

        vi.advanceTimersByTime(1200);

        expect(component.paymentState()).toBe('succeeded');
        expect(component.payment()).toEqual(succeededPayment);
      });

      it('clears the booking draft on success', () => {
        vi.spyOn(paymentService, 'recordPayment').mockReturnValue(of(succeededPayment));
        const clearSpy = vi.spyOn(bookingDraftService, 'clear');
        fillCardForm();

        component.payWithCard();
        vi.advanceTimersByTime(1200);

        expect(clearSpy).toHaveBeenCalled();
      });

      it('shows the decline reason and does not clear the draft when the card is declined', () => {
        vi.spyOn(paymentService, 'recordPayment').mockReturnValue(of(declinedPayment));
        const clearSpy = vi.spyOn(bookingDraftService, 'clear');
        fillCardForm({ cardNumber: '4000 0000 0000 0002' });

        component.payWithCard();
        vi.advanceTimersByTime(1200);

        expect(component.paymentState()).toBe('declined');
        expect(component.payment()?.failureReason).toBe('Your card was declined.');
        expect(clearSpy).not.toHaveBeenCalled();
      });

      it('retryPayment resets to the idle payment form', () => {
        vi.spyOn(paymentService, 'recordPayment').mockReturnValue(of(declinedPayment));
        fillCardForm({ cardNumber: '4000 0000 0000 0002' });
        component.payWithCard();
        vi.advanceTimersByTime(1200);

        component.retryPayment();

        expect(component.paymentState()).toBe('idle');
        expect(component.payment()).toBeNull();
      });

      describe('3D Secure challenge', () => {
        beforeEach(() => {
          vi.spyOn(paymentService, 'recordPayment').mockReturnValue(of(pending3dsPayment));
          fillCardForm({ cardNumber: '4000 0025 0000 3155' });
          component.payWithCard();
          vi.advanceTimersByTime(1200);
        });

        it('moves to the requires3ds state without clearing the draft', () => {
          const clearSpy = vi.spyOn(bookingDraftService, 'clear');

          expect(component.paymentState()).toBe('requires3ds');
          expect(component.payment()).toEqual(pending3dsPayment);
          expect(clearSpy).not.toHaveBeenCalled();
        });

        it('confirmThreeDs does nothing while the code is invalid', () => {
          const confirmSpy = vi.spyOn(paymentService, 'confirmThreeDs');
          component.threeDsForm.setValue({ code: '123' });

          component.confirmThreeDs();

          expect(confirmSpy).not.toHaveBeenCalled();
        });

        it('confirmThreeDs with the correct code succeeds and clears the draft', () => {
          vi.spyOn(paymentService, 'confirmThreeDs').mockReturnValue(of(succeededPayment));
          const clearSpy = vi.spyOn(bookingDraftService, 'clear');
          component.threeDsForm.setValue({ code: '123456' });

          component.confirmThreeDs();

          expect(paymentService.confirmThreeDs).toHaveBeenCalledWith(500, 3, '123456');
          expect(component.paymentState()).toBe('succeeded');
          expect(component.payment()).toEqual(succeededPayment);
          expect(clearSpy).toHaveBeenCalled();
        });

        it('confirmThreeDs with the wrong code declines the payment', () => {
          const failed: PaymentResponse = { ...declinedPayment, id: 3, failureReason: '3D Secure authentication failed.' };
          vi.spyOn(paymentService, 'confirmThreeDs').mockReturnValue(of(failed));

          component.threeDsForm.setValue({ code: '000000' });
          component.confirmThreeDs();

          expect(component.paymentState()).toBe('declined');
          expect(component.payment()?.failureReason).toBe('3D Secure authentication failed.');
        });

        it('retryPayment resets the 3DS code form', () => {
          component.threeDsForm.setValue({ code: '123456' });

          component.retryPayment();

          expect(component.threeDsForm.getRawValue().code).toBe('');
        });
      });

      it('sends a fresh idempotency reference on every attempt', () => {
        const recordSpy = vi.spyOn(paymentService, 'recordPayment').mockReturnValue(of(declinedPayment));
        fillCardForm({ cardNumber: '4000 0000 0000 0002' });
        component.payWithCard();
        vi.advanceTimersByTime(1200);
        const firstReference = recordSpy.mock.calls[0][1].reference;

        component.retryPayment();
        recordSpy.mockReturnValue(of(succeededPayment));
        fillCardForm();
        component.payWithCard();
        vi.advanceTimersByTime(1200);
        const secondReference = recordSpy.mock.calls[1][1].reference;

        expect(firstReference).not.toBe(secondReference);
      });

      it('does not submit while the seat hold has already expired', () => {
        component.holdExpired.set(true);
        fillCardForm();
        const paySpy = vi.spyOn(paymentService, 'recordPayment');

        component.payWithCard();

        expect(paySpy).not.toHaveBeenCalled();
      });
    });

    describe('pix payment', () => {
      beforeEach(() => {
        vi.useFakeTimers();
        proceedToPaymentStep();
        component.selectPaymentMethod('pix');
      });

      afterEach(() => vi.useRealTimers());

      it('pays with method "pix" and no card number', () => {
        const recordSpy = vi.spyOn(paymentService, 'recordPayment').mockReturnValue(of(succeededPayment));

        component.payWithPix();
        vi.advanceTimersByTime(1200);

        expect(recordSpy).toHaveBeenCalledWith(
          bookingDetail.booking.id,
          expect.objectContaining({ method: 'pix', cardNumber: null }),
        );
        expect(component.paymentState()).toBe('succeeded');
      });

      it('reuses the same reference on retry after a client-side error, so a response that actually succeeded server-side is recovered instead of double-submitted', () => {
        const recordSpy = vi
          .spyOn(paymentService, 'recordPayment')
          .mockReturnValueOnce(throwError(() => new Error('dropped connection')));

        component.payWithPix();
        vi.advanceTimersByTime(1200);

        expect(component.paymentState()).toBe('idle');
        const firstReference = recordSpy.mock.calls[0][1].reference;

        recordSpy.mockReturnValueOnce(of(succeededPayment));
        component.payWithPix();
        vi.advanceTimersByTime(1200);

        const secondReference = recordSpy.mock.calls[1][1].reference;
        expect(secondReference).toBe(firstReference);
        expect(component.paymentState()).toBe('succeeded');
      });
    });

    describe('viewBooking', () => {
      it('navigates to the booking details page', () => {
        proceedToPaymentStep();

        component.viewBooking();

        expect(router.navigate).toHaveBeenCalledWith(['/bookings', bookingDetail.booking.id]);
      });
    });
  });
});
