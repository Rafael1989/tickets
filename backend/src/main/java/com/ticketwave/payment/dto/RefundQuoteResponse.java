package com.ticketwave.payment.dto;

import java.math.BigDecimal;

/**
 * A non-mutating preview of what {@link com.ticketwave.payment.service.RefundService#initiateRefund}
 * would do, so a customer can see the policy outcome before committing to it.
 * policyCode/refundRate are null when ineligible (too close to departure), in
 * which case refundAmount is zero and nonRefundableAmount equals the fare.
 */
public record RefundQuoteResponse(
        Long bookingId,
        BigDecimal fareAmount,
        String policyCode,
        BigDecimal refundRate,
        BigDecimal refundAmount,
        BigDecimal nonRefundableAmount,
        String paymentMethod,
        boolean eligible
) {
}
