package com.ticketwave.pricing.dto;

import com.ticketwave.pricing.entity.PromoCode;

import java.math.BigDecimal;

public record PromoCodeApplication(
        PromoCode promoCode,
        BigDecimal discountAmount
) {
}
