package com.ticketwave.pricing.dto;

import com.ticketwave.pricing.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public record PromoCodeResponse(
        Long id,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        Instant validFrom,
        Instant validTo,
        Integer maxRedemptions,
        Integer redemptionCount,
        Boolean active,
        Instant createdAt
) {
}
