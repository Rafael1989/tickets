package com.ticketwave.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * overrideAmount/reason let a SUPPORT or ADMIN agent waive part or all of the
 * policy-computed cancellation fee when approving a refund - see
 * RefundServiceImpl.processRefund for the validation and audit trail this
 * drives. Both are ignored on a REJECT decision.
 */
public record RefundDecisionRequest(
        @NotNull RefundDecision decision,
        @DecimalMin(value = "0.00", message = "overrideAmount must not be negative") BigDecimal overrideAmount,
        @Size(max = 500, message = "reason must be at most 500 characters") String reason
) {
}
