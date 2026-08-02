import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OperatorReportService } from './operator-report.service';

describe('OperatorReportService', () => {
  let service: OperatorReportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OperatorReportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getReport gets /api/operator/reports', () => {
    service.getReport().subscribe();

    const req = httpMock.expectOne('/api/operator/reports');
    expect(req.request.method).toBe('GET');
    req.flush({ routes: [], totalConfirmedBookings: 0, totalRevenue: 0 });
  });
});
