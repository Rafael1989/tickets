package com.ticketwave.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Fired to a partner's registered "BOOKING_CANCELLED" webhook when a cancellation refund is approved. */
public record BookingCancelledWebhookPayload(
        Long bookingId,
        String pnr,
        Long refundId,
        BigDecimal refundAmount,
        String policyCode,
        Instant occurredAt
) {
}
