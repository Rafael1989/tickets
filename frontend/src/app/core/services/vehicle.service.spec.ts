import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { VehicleRequest } from '../models/route.model';
import { VehicleService } from './vehicle.service';

describe('VehicleService', () => {
  let service: VehicleService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(VehicleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createVehicle posts the request to /api/vehicles', () => {
    const request: VehicleRequest = { type: 'BUS', identifier: 'BUS-1234', capacity: 45 };

    service.createVehicle(request).subscribe();

    const req = httpMock.expectOne('/api/vehicles');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('listMyVehicles requests /api/vehicles/mine', () => {
    service.listMyVehicles().subscribe();

    const req = httpMock.expectOne('/api/vehicles/mine');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
