package com.ticketwave.payment.dto;

import com.ticketwave.payment.entity.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundResponse(
        Long id,
        Long paymentId,
        BigDecimal amount,
        String policyCode,
        RefundStatus status,
        Long processedByUserId,
        Instant processedAt,
        BigDecimal overrideDelta,
        String overrideReason
) {
}
