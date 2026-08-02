package com.ticketwave.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * bookingId is deliberately not a field here: it's supplied as the explicit
 * path/method parameter wherever this is used, so there's no way for a
 * request body to disagree with which booking it's paying for. reference is
 * the caller-supplied idempotency key; status and paidAt are
 * server-controlled and set once the payment is processed.
 *
 * cardNumber is only meaningful for method "card" (the simulated gateway
 * reads it in-memory to decide approve/decline against a handful of known
 * test numbers) and is never persisted or echoed back — matching the same
 * PCI-scope-reduction intent a real tokenizing gateway would have, just
 * without an actual gateway behind it.
 */
public record PaymentRequest(
        // 0.00 is allowed, not 0.01: a promo code can legitimately discount a booking to nothing
        // (a 100%-off comp), and such a booking still has to travel the normal
        // INITIATED -> PAYMENT_PROCESSING -> CONFIRMED path — this API never confirms a booking
        // without a payment behind it. Rejecting zero here left those bookings stuck in INITIATED
        // with no way to complete them. recordPayment still requires the amount to equal the
        // booking's own total, so this can't be used to underpay a non-zero booking.
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 30) String method,
        @NotBlank @Size(max = 100) String reference,
        @Pattern(regexp = "[0-9 ]{12,24}") String cardNumber
) {
}
