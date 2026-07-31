import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ScheduleRequest, SeatRequest, SeatUpdateRequest } from '../models/route.model';
import { InventoryManagementService } from './inventory-management.service';

describe('InventoryManagementService', () => {
  let service: InventoryManagementService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InventoryManagementService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createSchedule posts the request to /api/schedules', () => {
    const request: ScheduleRequest = {
      routeId: 1,
      departureTime: '2026-08-01T10:00:00Z',
      arrivalTime: '2026-08-01T12:00:00Z',
      baseFare: 100,
      currency: 'USD',
    };

    service.createSchedule(request).subscribe();

    const req = httpMock.expectOne('/api/schedules');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('addSeat posts the request to /api/seats', () => {
    const request: SeatRequest = {
      scheduleId: 1,
      seatNumber: '1A',
      seatClass: 'ECONOMY',
      priceModifier: 1,
    };

    service.addSeat(request).subscribe();

    const req = httpMock.expectOne('/api/seats');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('updateSeat puts the request to /api/seats/{id}', () => {
    const request: SeatUpdateRequest = { status: 'HELD', priceModifier: 1.5 };

    service.updateSeat(9, request).subscribe();

    const req = httpMock.expectOne('/api/seats/9');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });
});
