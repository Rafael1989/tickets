package com.ticketwave.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * totalRefunds and totalAdjustments are reported as positive magnitudes
 * (money moved), even though LedgerEntry itself stores them signed —
 * netAmount is where the sign matters, and is the true payments-minus-
 * outflows figure for the range.
 */
public record ReconciliationReportResponse(
        Instant from,
        Instant to,
        BigDecimal totalPayments,
        long paymentCount,
        BigDecimal totalRefunds,
        long refundCount,
        BigDecimal totalAdjustments,
        long adjustmentCount,
        BigDecimal netAmount
) {
}
