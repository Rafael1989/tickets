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

  it('listMyBookings requests /api/bookings/me', () => {
    service.listMyBookings().subscribe();

    const req = httpMock.expectOne('/api/bookings/me');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('getBookingByPnr requests /api/bookings/pnr/{pnr}, url-encoded', () => {
    service.getBookingByPnr('ABC 123').subscribe();

    const req = httpMock.expectOne('/api/bookings/pnr/ABC%20123');
    expect(req.request.method).toBe('GET');
    req.flush({ booking: {}, items: [] });
  });

  it('lookupByPnrAndEmail gets /api/bookings/pnr/{pnr}/lookup with the email param, url-encoded', () => {
    service.lookupByPnrAndEmail('ABC 123', 'alice@example.com').subscribe();

    const req = httpMock.expectOne('/api/bookings/pnr/ABC%20123/lookup?email=alice%40example.com');
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

  it('getRescheduleQuote gets /api/bookings/{id}/reschedule-quote with scheduleId and seatIds', () => {
    service.getRescheduleQuote(7, 2, [8, 9]).subscribe();

    const req = httpMock.expectOne('/api/bookings/7/reschedule-quote?scheduleId=2&seatIds=8&seatIds=9');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('searchBookings gets /api/bookings/search with the query param', () => {
    service.searchBookings('alice').subscribe();

    const req = httpMock.expectOne('/api/bookings/search?query=alice');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
