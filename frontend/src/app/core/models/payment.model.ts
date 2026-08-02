export type PaymentStatus = 'SUCCEEDED' | 'FAILED' | 'REFUNDED' | 'PENDING_3DS';

export type RefundStatus = 'PENDING' | 'PROCESSED' | 'REJECTED';

export interface PaymentRequest {
  amount: number;
  method: string;
  reference: string;
  /** Only meaningful for method "card" — never persisted server-side, only used to decide the simulated gateway's outcome. */
  cardNumber?: string | null;
}

export interface PaymentResponse {
  id: number;
  bookingId: number;
  amount: number;
  method: string;
  reference: string;
  status: PaymentStatus;
  paidAt: string | null;
  failureReason: string | null;
}

export interface RefundResponse {
  id: number;
  paymentId: number;
  amount: number;
  policyCode: string;
  status: RefundStatus;
  processedByUserId: number | null;
  processedAt: string | null;
  /** Signed: positive means a support/admin agent waived more than the policy amount. Null if no override was applied. */
  overrideDelta: number | null;
  overrideReason: string | null;
}

export interface RefundQuoteResponse {
  bookingId: number;
  fareAmount: number;
  policyCode: string | null;
  refundRate: number | null;
  refundAmount: number;
  nonRefundableAmount: number;
  paymentMethod: string;
  eligible: boolean;
}

export type RefundDecision = 'APPROVE' | 'REJECT';

/**
 * overrideAmount/reason let a SUPPORT/ADMIN agent waive part or all of the
 * policy-computed cancellation fee on approval — both are ignored on a
 * REJECT decision, and reason is mandatory whenever overrideAmount is set.
 */
export interface RefundDecisionRequest {
  decision: RefundDecision;
  overrideAmount?: number | null;
  reason?: string | null;
}
