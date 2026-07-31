import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RefundDecision, RefundResponse } from '../models/payment.model';

@Injectable({ providedIn: 'root' })
export class RefundService {
  constructor(private readonly http: HttpClient) {}

  initiateRefund(bookingId: number): Observable<RefundResponse> {
    return this.http.post<RefundResponse>(`/api/bookings/${bookingId}/refunds`, {});
  }

  processRefund(refundId: number, decision: RefundDecision): Observable<RefundResponse> {
    return this.http.put<RefundResponse>(`/api/refunds/${refundId}/process`, { decision });
  }
}
