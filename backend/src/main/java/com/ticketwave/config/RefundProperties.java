package com.ticketwave.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * The cancellation/refund policy: full refund far enough out, a prorated
 * partial refund closer in, and cancellation blocked entirely once departure
 * is imminent.
 */
@ConfigurationProperties(prefix = "ticketwave.refund")
public record RefundProperties(
        long fullRefundThresholdDays,
        long partialRefundThresholdHours,
        BigDecimal partialRefundRate
) {
}
