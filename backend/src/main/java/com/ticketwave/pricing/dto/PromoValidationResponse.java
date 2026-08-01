package com.ticketwave.pricing.dto;

import java.math.BigDecimal;

public record PromoValidationResponse(
        String code,
        BigDecimal discountAmount,
        BigDecimal totalAfterDiscount
) {
}
