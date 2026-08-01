import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouteRequest } from '../models/route.model';
import { RouteService } from './route.service';

describe('RouteService', () => {
  let service: RouteService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RouteService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createRoute posts the request to /api/routes', () => {
    const request: RouteRequest = {
      type: 'BUS',
      origin: 'City A',
      destination: 'City B',
      durationMinutes: 120,
    };

    service.createRoute(request).subscribe();

    const req = httpMock.expectOne('/api/routes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ id: 1, operatorId: 2, ...request, venue: null });
  });

  it('updateRoute puts the request to /api/routes/{id}', () => {
    const request: RouteRequest = {
      type: 'TRAIN',
      origin: 'City A',
      destination: 'City B',
      durationMinutes: 90,
    };

    service.updateRoute(5, request).subscribe();

    const req = httpMock.expectOne('/api/routes/5');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ id: 5, operatorId: 2, ...request, venue: null });
  });

  it('listMyRoutes requests /api/routes/mine', () => {
    service.listMyRoutes().subscribe();

    const req = httpMock.expectOne('/api/routes/mine');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('listSchedulesForRoute requests /api/routes/{id}/schedules', () => {
    service.listSchedulesForRoute(5).subscribe();

    const req = httpMock.expectOne('/api/routes/5/schedules');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
