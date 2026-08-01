export type PaymentStatus = 'SUCCEEDED' | 'FAILED' | 'REFUNDED';

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

export interface RefundDecisionRequest {
  decision: RefundDecision;
}
