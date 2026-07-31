import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
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

  const detail: BookingDetailResponse = {
    booking: {
      id: 500,
      userId: 1,
      scheduleId: 1,
      pnr: 'ABC123',
      bookingTime: '2026-01-01T00:00:00Z',
      status: 'CONFIRMED',
      totalAmount: 20,
      promoCode: null,
    },
    items: [{ id: 1, bookingId: 500, seatId: 1, passengerId: 100, fare: 20 }],
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

  it('lookupByPnr does nothing for a blank PNR', () => {
    const getSpy = vi.spyOn(bookingService, 'getBookingByPnr');
    component.pnrForm.setValue({ pnr: '   ' });

    component.lookupByPnr();

    expect(getSpy).not.toHaveBeenCalled();
  });

  it('lookupByPnr trims and looks up the PNR, storing the result', () => {
    vi.spyOn(bookingService, 'getBookingByPnr').mockReturnValue(of(detail));
    component.pnrForm.setValue({ pnr: ' ABC123 ' });

    component.lookupByPnr();

    expect(bookingService.getBookingByPnr).toHaveBeenCalledWith('ABC123');
    expect(component.booking()).toEqual(detail);
    expect(component.searchError()).toBe(false);
  });

  it('lookupByPnr sets searchError when the booking is not found', () => {
    vi.spyOn(bookingService, 'getBookingByPnr').mockReturnValue(throwError(() => new Error('404')));
    component.pnrForm.setValue({ pnr: 'MISSING' });

    component.lookupByPnr();

    expect(component.searchError()).toBe(true);
    expect(component.booking()).toBeNull();
  });

  it('processRefund does nothing while the form is invalid', () => {
    const processSpy = vi.spyOn(refundService, 'processRefund');
    component.refundForm.patchValue({ refundId: null });

    component.processRefund();

    expect(processSpy).not.toHaveBeenCalled();
  });

  it('processRefund submits the refund id and decision, notifying on success', () => {
    const refund: RefundResponse = {
      id: 9,
      paymentId: 1,
      amount: 20,
      policyCode: 'FULL',
      status: 'PROCESSED',
      processedByUserId: 3,
      processedAt: '2026-01-02T00:00:00Z',
    };
    vi.spyOn(refundService, 'processRefund').mockReturnValue(of(refund));
    const successSpy = vi.spyOn(notifications, 'success');
    component.refundForm.setValue({ refundId: 9, decision: 'APPROVE' });

    component.processRefund();

    expect(refundService.processRefund).toHaveBeenCalledWith(9, 'APPROVE');
    expect(successSpy).toHaveBeenCalledWith("Refund #9 is now PROCESSED.");
  });
});
