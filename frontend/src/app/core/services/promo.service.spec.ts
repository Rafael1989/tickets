import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PromoService } from './promo.service';

describe('PromoService', () => {
  let service: PromoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PromoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('validate posts to /api/promos/validate', () => {
    service.validate({ code: 'SAVE20', subtotal: 100 }).subscribe();

    const req = httpMock.expectOne('/api/promos/validate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'SAVE20', subtotal: 100 });
    req.flush({ code: 'SAVE20', discountAmount: 20, totalAfterDiscount: 80 });
  });

  it('listPromoCodes gets /api/promos', () => {
    service.listPromoCodes().subscribe();

    const req = httpMock.expectOne('/api/promos');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('createPromoCode posts to /api/promos', () => {
    const request = {
      code: 'SAVE20',
      discountType: 'PERCENTAGE' as const,
      discountValue: 20,
      validFrom: '2026-01-01T00:00:00.000Z',
      validTo: '2026-12-31T00:00:00.000Z',
      maxRedemptions: 100,
    };

    service.createPromoCode(request).subscribe();

    const req = httpMock.expectOne('/api/promos');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('updateStatus puts to /api/promos/{id}/status', () => {
    service.updateStatus(9, false).subscribe();

    const req = httpMock.expectOne('/api/promos/9/status');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ active: false });
    req.flush({});
  });
});
