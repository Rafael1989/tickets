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
}
