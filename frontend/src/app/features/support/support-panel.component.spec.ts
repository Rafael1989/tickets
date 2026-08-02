import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { BookingDetailResponse, BookingSearchResult } from '../../core/models/booking.model';
import { RefundResponse } from '../../core/models/payment.model';
import { BookingService } from '../../core/services/booking.service';
import { NotificationService } from '../../core/services/notification.service';
import { RefundService } from '../../core/services/refund.service';
import { SupportPanelComponent } from './support-panel.component';

describe('SupportPanelComponent', () => {
  let fixture: ComponentFixture<SupportPanelComponent>;
  let component: SupportPanelComponent;
  let bookingService: BookingService;
  let refundService: RefundService;
  let notifications: NotificationService;

  const searchResult: BookingSearchResult = {
    bookingId: 500,
    pnr: 'ABC123',
    status: 'CONFIRMED',
    totalAmount: 100,
    bookingTime: '2026-01-01T00:00:00Z',
    customerUsername: 'alice',
    customerEmail: 'alice@example.com',
    origin: 'NYC',
    destination: 'Boston',
    departureTime: '2026-02-01T00:00:00Z',
  };

  const detail: BookingDetailResponse = {
    booking: {
      id: 500,
      userId: 1,
      scheduleId: 1,
      pnr: 'ABC123',
      bookingTime: '2026-01-01T00:00:00Z',
      status: 'CANCELLED',
      totalAmount: 100,
      promoCode: null,
    },
    items: [{ id: 1, bookingId: 500, seatId: 1, passengerId: 100, fare: 100 }],
  };

  const pendingRefund: RefundResponse = {
    id: 9,
    paymentId: 1,
    amount: 50,
    policyCode: 'PARTIAL_REFUND',
    status: 'PENDING',
    processedByUserId: null,
    processedAt: null,
    overrideDelta: null,
    overrideReason: null,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SupportPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    bookingService = TestBed.inject(BookingService);
    refundService = TestBed.inject(RefundService);
    notifications = TestBed.inject(NotificationService);

    fixture = TestBed.createComponent(SupportPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('search does nothing for a blank query', () => {
    const searchSpy = vi.spyOn(bookingService, 'searchBookings');
    component.searchForm.setValue({ query: '   ' });

    component.search();

    expect(searchSpy).not.toHaveBeenCalled();
  });

  it('search trims the query and stores the results', () => {
    vi.spyOn(bookingService, 'searchBookings').mockReturnValue(of([searchResult]));
    component.searchForm.setValue({ query: ' alice ' });

    component.search();

    expect(bookingService.searchBookings).toHaveBeenCalledWith('alice');
    expect(component.results()).toEqual([searchResult]);
    expect(component.searchError()).toBe(false);
  });

  it('search sets searchError on failure', () => {
    vi.spyOn(bookingService, 'searchBookings').mockReturnValue(throwError(() => new Error('500')));
    component.searchForm.setValue({ query: 'alice' });

    component.search();

    expect(component.searchError()).toBe(true);
  });

  it('selectBooking loads the booking detail and its refund history together', () => {
    vi.spyOn(bookingService, 'getBooking').mockReturnValue(of(detail));
    vi.spyOn(refundService, 'listRefundsForBooking').mockReturnValue(of([pendingRefund]));

    component.selectBooking(500);

    expect(bookingService.getBooking).toHaveBeenCalledWith(500);
    expect(refundService.listRefundsForBooking).toHaveBeenCalledWith(500);
    expect(component.detail()).toEqual(detail);
    expect(component.refunds()).toEqual([pendingRefund]);
    expect(component.overrideForm.controls.approveAmount.value).toBe(50);
  });

  it('selectBooking sets detailError on failure', () => {
    vi.spyOn(bookingService, 'getBooking').mockReturnValue(throwError(() => new Error('404')));
    vi.spyOn(refundService, 'listRefundsForBooking').mockReturnValue(of([]));

    component.selectBooking(500);

    expect(component.detailError()).toBe(true);
  });

  it('closeDetail clears the detail and refund state', () => {
    vi.spyOn(bookingService, 'getBooking').mockReturnValue(of(detail));
    vi.spyOn(refundService, 'listRefundsForBooking').mockReturnValue(of([pendingRefund]));
    component.selectBooking(500);

    component.closeDetail();

    expect(component.detail()).toBeNull();
    expect(component.refunds()).toEqual([]);
  });

  describe('with a pending refund loaded', () => {
    beforeEach(() => {
      vi.spyOn(bookingService, 'getBooking').mockReturnValue(of(detail));
      vi.spyOn(refundService, 'listRefundsForBooking').mockReturnValue(of([pendingRefund]));
      component.selectBooking(500);
    });

    it('toggleWaiveFee sets the approve amount to the full fare when enabled', () => {
      component.overrideForm.controls.waiveFee.setValue(true);

      component.toggleWaiveFee();

      expect(component.overrideForm.controls.approveAmount.value).toBe(100);
      expect(component.waivedAmount()).toBe(50);
    });

    it('toggleWaiveFee resets the approve amount to the policy amount when disabled', () => {
      component.overrideForm.controls.waiveFee.setValue(true);
      component.toggleWaiveFee();
      component.overrideForm.controls.waiveFee.setValue(false);

      component.toggleWaiveFee();

      expect(component.overrideForm.controls.approveAmount.value).toBe(50);
    });

    it('approveRefund with the fee waived but no reason shows an error and does not call the service', () => {
      const processSpy = vi.spyOn(refundService, 'processRefund');
      const errorSpy = vi.spyOn(notifications, 'error');
      component.overrideForm.setValue({ waiveFee: true, approveAmount: 100, reason: '   ' });

      component.approveRefund();

      expect(processSpy).not.toHaveBeenCalled();
      expect(errorSpy).toHaveBeenCalled();
    });

    it('approveRefund without a waiver submits a plain approval', () => {
      vi.spyOn(refundService, 'processRefund').mockReturnValue(of({ ...pendingRefund, status: 'PROCESSED' }));
      const successSpy = vi.spyOn(notifications, 'success');

      component.approveRefund();

      expect(refundService.processRefund).toHaveBeenCalledWith(9, 'APPROVE', null, null);
      expect(successSpy).toHaveBeenCalledWith('Refund #9 is now PROCESSED.');
    });

    it('approveRefund with a valid waiver submits the override amount and reason', () => {
      vi.spyOn(refundService, 'processRefund').mockReturnValue(
        of({ ...pendingRefund, status: 'PROCESSED', amount: 100, overrideDelta: 50, overrideReason: 'Goodwill' }),
      );
      component.overrideForm.setValue({ waiveFee: true, approveAmount: 100, reason: 'Goodwill' });

      component.approveRefund();

      expect(refundService.processRefund).toHaveBeenCalledWith(9, 'APPROVE', 100, 'Goodwill');
    });

    it('rejectRefund submits a rejection without any override', () => {
      vi.spyOn(refundService, 'processRefund').mockReturnValue(of({ ...pendingRefund, status: 'REJECTED' }));

      component.rejectRefund();

      expect(refundService.processRefund).toHaveBeenCalledWith(9, 'REJECT', null, null);
    });
  });
});
