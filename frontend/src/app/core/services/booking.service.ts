import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BookingDetailResponse,
  CreateBookingRequest,
  RescheduleQuoteResponse,
  RescheduleRequest,
} from '../models/booking.model';

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

  getRescheduleQuote(bookingId: number, scheduleId: number, seatIds: number[]): Observable<RescheduleQuoteResponse> {
    const params = new URLSearchParams();
    params.set('scheduleId', String(scheduleId));
    for (const seatId of seatIds) {
      params.append('seatIds', String(seatId));
    }
    return this.http.get<RescheduleQuoteResponse>(`/api/bookings/${bookingId}/reschedule-quote?${params.toString()}`);
  }
}
