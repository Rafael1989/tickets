package com.ticketwave.payment.dto;

import com.ticketwave.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long bookingId,
        BigDecimal amount,
        String method,
        String reference,
        PaymentStatus status,
        Instant paidAt
) {
}
