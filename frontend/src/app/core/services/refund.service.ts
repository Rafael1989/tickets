import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RefundDecision, RefundQuoteResponse, RefundResponse } from '../models/payment.model';

@Injectable({ providedIn: 'root' })
export class RefundService {
  constructor(private readonly http: HttpClient) {}

  getRefundQuote(bookingId: number): Observable<RefundQuoteResponse> {
    return this.http.get<RefundQuoteResponse>(`/api/bookings/${bookingId}/refund-quote`);
  }

  initiateRefund(bookingId: number): Observable<RefundResponse> {
    return this.http.post<RefundResponse>(`/api/bookings/${bookingId}/refunds`, {});
  }

  /** Newest first; typically zero or one, but a downward reschedule can also leave a RESCHEDULE_CREDIT refund. */
  listRefundsForBooking(bookingId: number): Observable<RefundResponse[]> {
    return this.http.get<RefundResponse[]>(`/api/bookings/${bookingId}/refunds`);
  }

  /**
   * overrideAmount/reason waive part or all of the policy-computed fee on
   * approval — omit both for an ordinary approve/reject.
   */
  processRefund(
    refundId: number,
    decision: RefundDecision,
    overrideAmount?: number | null,
    reason?: string | null,
  ): Observable<RefundResponse> {
    return this.http.put<RefundResponse>(`/api/refunds/${refundId}/process`, { decision, overrideAmount, reason });
  }
}
