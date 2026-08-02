package com.ticketwave.pricing.dto;

import com.ticketwave.pricing.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record PromoCodeRequest(
        @NotBlank @Size(max = 30) String code,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.01") BigDecimal discountValue,
        @NotNull Instant validFrom,
        @NotNull @Future Instant validTo,
        @Positive Integer maxRedemptions
) {
}
