package com.ticketwave.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * bookingId is deliberately not a field here: it's supplied as the explicit
 * path/method parameter wherever this is used, so there's no way for a
 * request body to disagree with which booking it's paying for. reference is
 * the caller-supplied idempotency key; status and paidAt are
 * server-controlled and set once the payment is processed.
 */
public record PaymentRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 30) String method,
        @NotBlank @Size(max = 100) String reference
) {
}
