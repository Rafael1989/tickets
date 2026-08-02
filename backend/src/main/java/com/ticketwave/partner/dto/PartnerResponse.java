package com.ticketwave.partner.dto;

import com.ticketwave.partner.entity.PartnerStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PartnerResponse(
        Long id,
        String name,
        String contactEmail,
        PartnerStatus status,
        BigDecimal commissionRate,
        Instant createdAt
) {
}
