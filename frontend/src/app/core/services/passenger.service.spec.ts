import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PassengerRequest } from '../models/passenger.model';
import { PassengerService } from './passenger.service';

describe('PassengerService', () => {
  let service: PassengerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PassengerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createPassenger posts to /api/passengers', () => {
    const request: PassengerRequest = {
      fullName: 'Jane Doe',
      dob: '1990-01-01',
      idType: 'passport',
      idNumber: 'X123456',
    };

    service.createPassenger(request).subscribe();

    const req = httpMock.expectOne('/api/passengers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ id: 1, userId: 1, ...request });
  });

  it('listMyPassengers requests /api/passengers/me', () => {
    service.listMyPassengers().subscribe();

    const req = httpMock.expectOne('/api/passengers/me');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('updatePassenger puts to /api/passengers/:id', () => {
    const request: PassengerRequest = {
      fullName: 'Jane Updated',
      dob: '1990-01-01',
      idType: 'passport',
      idNumber: 'X123456',
    };

    service.updatePassenger(1, request).subscribe();

    const req = httpMock.expectOne('/api/passengers/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ id: 1, userId: 1, ...request });
  });

  it('deletePassenger deletes /api/passengers/:id', () => {
    service.deletePassenger(1).subscribe();

    const req = httpMock.expectOne('/api/passengers/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
