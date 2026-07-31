import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BookingDetailResponse, CreateBookingRequest, RescheduleRequest } from '../models/booking.model';

@Injectable({ providedIn: 'root' })
export class BookingService {
  constructor(private readonly http: HttpClient) {}

  createBooking(request: CreateBookingRequest): Observable<BookingDetailResponse> {
    return this.http.post<BookingDetailResponse>('/api/bookings', request);
  }

  getBooking(bookingId: number): Observable<BookingDetailResponse> {
    return this.http.get<BookingDetailResponse>(`/api/bookings/${bookingId}`);
  }

  getBookingByPnr(pnr: string): Observable<BookingDetailResponse> {
    return this.http.get<BookingDetailResponse>(`/api/bookings/pnr/${encodeURIComponent(pnr)}`);
  }

  rescheduleBooking(bookingId: number, request: RescheduleRequest): Observable<BookingDetailResponse> {
    return this.http.put<BookingDetailResponse>(`/api/bookings/${bookingId}/reschedule`, request);
  }
}
