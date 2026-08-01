import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DriverRequest } from '../models/route.model';
import { DriverService } from './driver.service';

describe('DriverService', () => {
  let service: DriverService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DriverService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createDriver posts the request to /api/drivers', () => {
    const request: DriverRequest = { fullName: 'Jane Doe', licenseNumber: 'LIC-123' };

    service.createDriver(request).subscribe();

    const req = httpMock.expectOne('/api/drivers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('listMyDrivers requests /api/drivers/mine', () => {
    service.listMyDrivers().subscribe();

    const req = httpMock.expectOne('/api/drivers/mine');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
