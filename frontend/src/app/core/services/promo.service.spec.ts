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
});
