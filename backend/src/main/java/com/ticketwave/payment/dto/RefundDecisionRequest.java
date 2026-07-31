package com.ticketwave.payment.dto;

import jakarta.validation.constraints.NotNull;

public record RefundDecisionRequest(
        @NotNull RefundDecision decision
) {
}
