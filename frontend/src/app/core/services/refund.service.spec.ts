import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RefundService } from './refund.service';

describe('RefundService', () => {
  let service: RefundService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RefundService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('initiateRefund posts to /api/bookings/{id}/refunds', () => {
    service.initiateRefund(500).subscribe();

    const req = httpMock.expectOne('/api/bookings/500/refunds');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({});
  });

  it('processRefund puts the decision to /api/refunds/{id}/process', () => {
    service.processRefund(9, 'APPROVE').subscribe();

    const req = httpMock.expectOne('/api/refunds/9/process');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ decision: 'APPROVE' });
    req.flush({});
  });
});
