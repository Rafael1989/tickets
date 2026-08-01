package com.ticketwave.pricing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PromoValidationRequest(
        @NotBlank @Size(max = 30) String code,
        @NotNull @DecimalMin(value = "0.00") BigDecimal subtotal
) {
}
