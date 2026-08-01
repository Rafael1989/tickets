import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ScheduleService } from './schedule.service';

describe('ScheduleService', () => {
  let service: ScheduleService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ScheduleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getSchedule requests /api/schedules/{id}', () => {
    service.getSchedule(42).subscribe();

    const req = httpMock.expectOne('/api/schedules/42');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('getSeats requests /api/schedules/{id}/seats', () => {
    service.getSeats(42).subscribe();

    const req = httpMock.expectOne('/api/schedules/42/seats');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('holdSeat posts to /api/schedules/{id}/seats/{seatId}/hold', () => {
    service.holdSeat(42, 7).subscribe();

    const req = httpMock.expectOne('/api/schedules/42/seats/7/hold');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('releaseSeat deletes /api/schedules/{id}/seats/{seatId}/hold', () => {
    service.releaseSeat(42, 7).subscribe();

    const req = httpMock.expectOne('/api/schedules/42/seats/7/hold');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
