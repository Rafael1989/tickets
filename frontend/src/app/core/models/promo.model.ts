export interface PromoValidationRequest {
  code: string;
  subtotal: number;
}

export interface PromoValidationResponse {
  code: string;
  discountAmount: number;
  totalAfterDiscount: number;
}

export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';

/** validFrom/validTo are ISO instants (datetime-local inputs are converted via new Date(value).toISOString() before sending). */
export interface PromoCodeRequest {
  code: string;
  discountType: DiscountType;
  discountValue: number;
  validFrom: string;
  validTo: string;
  maxRedemptions: number | null;
}

export interface PromoCodeResponse {
  id: number;
  code: string;
  discountType: DiscountType;
  discountValue: number;
  validFrom: string;
  validTo: string;
  maxRedemptions: number | null;
  redemptionCount: number;
  active: boolean;
  createdAt: string;
}
