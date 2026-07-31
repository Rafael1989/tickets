import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreateBookingRequest, RescheduleRequest } from '../models/booking.model';
import { BookingService } from './booking.service';

describe('BookingService', () => {
  let service: BookingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BookingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createBooking posts the request to /api/bookings', () => {
    const request: CreateBookingRequest = {
      scheduleId: 1,
      seatSelections: [{ seatId: 5, passengerId: 100 }],
      promoCode: null,
    };

    service.createBooking(request).subscribe();

    const req = httpMock.expectOne('/api/bookings');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ booking: {}, items: [] });
  });

  it('getBooking requests /api/bookings/{id}', () => {
    service.getBooking(7).subscribe();

    const req = httpMock.expectOne('/api/bookings/7');
    expect(req.request.method).toBe('GET');
    req.flush({ booking: {}, items: [] });
  });

  it('getBookingByPnr requests /api/bookings/pnr/{pnr}, url-encoded', () => {
    service.getBookingByPnr('ABC 123').subscribe();

    const req = httpMock.expectOne('/api/bookings/pnr/ABC%20123');
    expect(req.request.method).toBe('GET');
    req.flush({ booking: {}, items: [] });
  });

  it('rescheduleBooking puts the request to /api/bookings/{id}/reschedule', () => {
    const request: RescheduleRequest = {
      scheduleId: 2,
      seatSelections: [{ seatId: 8, passengerId: 100 }],
    };

    service.rescheduleBooking(7, request).subscribe();

    const req = httpMock.expectOne('/api/bookings/7/reschedule');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ booking: {}, items: [] });
  });
});
