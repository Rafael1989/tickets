package com.ticketwave.pricing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FareRuleResponse(
        Long id,
        Long routeId,
        String seatClass,
        Instant validFrom,
        Instant validTo,
        BigDecimal surchargeRate
) {
}
