export interface PromoValidationRequest {
  code: string;
  subtotal: number;
}

export interface PromoValidationResponse {
  code: string;
  discountAmount: number;
  totalAfterDiscount: number;
}
