import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FareRuleRequest } from '../models/fare-rule.model';
import { FareRuleService } from './fare-rule.service';

describe('FareRuleService', () => {
  let service: FareRuleService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FareRuleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createFareRule posts the request to /api/fare-rules', () => {
    const request: FareRuleRequest = {
      routeId: 1,
      seatClass: 'business',
      validFrom: '2026-12-01T00:00:00Z',
      validTo: '2026-12-31T23:59:59Z',
      surchargeRate: 0.2,
    };

    service.createFareRule(request).subscribe();

    const req = httpMock.expectOne('/api/fare-rules');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('bulkCreateFareRules posts the array to /api/fare-rules/bulk', () => {
    const requests: FareRuleRequest[] = [
      { routeId: 1, seatClass: 'business', validFrom: '2026-12-01T00:00:00Z', validTo: '2026-12-31T23:59:59Z', surchargeRate: 0.2 },
    ];

    service.bulkCreateFareRules(requests).subscribe();

    const req = httpMock.expectOne('/api/fare-rules/bulk');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(requests);
    req.flush([]);
  });

  it('listFareRulesForRoute requests /api/routes/{id}/fare-rules', () => {
    service.listFareRulesForRoute(1).subscribe();

    const req = httpMock.expectOne('/api/routes/1/fare-rules');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
