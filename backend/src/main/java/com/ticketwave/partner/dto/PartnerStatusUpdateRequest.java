package com.ticketwave.partner.dto;

import com.ticketwave.partner.entity.PartnerStatus;
import jakarta.validation.constraints.NotNull;

public record PartnerStatusUpdateRequest(
        @NotNull PartnerStatus status
) {
}
