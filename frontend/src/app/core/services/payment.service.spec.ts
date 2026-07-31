import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PaymentRequest } from '../models/payment.model';
import { PaymentService } from './payment.service';

describe('PaymentService', () => {
  let service: PaymentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PaymentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('recordPayment posts to /api/bookings/{id}/payments', () => {
    const request: PaymentRequest = { amount: 20, method: 'card', reference: 'ref-1' };

    service.recordPayment(500, request).subscribe();

    const req = httpMock.expectOne('/api/bookings/500/payments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({
      id: 1,
      bookingId: 500,
      amount: 20,
      method: 'card',
      reference: 'ref-1',
      status: 'SUCCEEDED',
      paidAt: '2026-01-01T00:00:00Z',
    });
  });
});
