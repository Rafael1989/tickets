import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PaymentRequest, PaymentResponse } from '../models/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  constructor(private readonly http: HttpClient) {}

  recordPayment(bookingId: number, request: PaymentRequest): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`/api/bookings/${bookingId}/payments`, request);
  }

  /** Settles a PENDING_3DS payment's simulated challenge — a code mismatch fails the payment/booking exactly like an ordinary decline. */
  confirmThreeDs(bookingId: number, paymentId: number, code: string): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`/api/bookings/${bookingId}/payments/${paymentId}/confirm-3ds`, { code });
  }
}
