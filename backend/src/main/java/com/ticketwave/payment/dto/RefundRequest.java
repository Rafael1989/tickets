package com.ticketwave.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * status, processedBy, and processedAt are server-controlled: status starts
 * PENDING, and processedBy is resolved from the authenticated principal, not
 * accepted from the request body.
 */
public record RefundRequest(
        @NotNull Long paymentId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 50) String policyCode
) {
}
