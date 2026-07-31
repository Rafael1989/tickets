export type PaymentStatus = 'SUCCEEDED' | 'FAILED' | 'REFUNDED';

export type RefundStatus = 'PENDING' | 'PROCESSED' | 'REJECTED';

export interface PaymentRequest {
  amount: number;
  method: string;
  reference: string;
}

export interface PaymentResponse {
  id: number;
  bookingId: number;
  amount: number;
  method: string;
  reference: string;
  status: PaymentStatus;
  paidAt: string;
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

export type RefundDecision = 'APPROVE' | 'REJECT';

export interface RefundDecisionRequest {
  decision: RefundDecision;
}
