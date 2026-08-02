package com.ticketwave.partner.dto;

import com.ticketwave.partner.entity.WebhookStatus;
import jakarta.validation.constraints.NotNull;

public record WebhookStatusUpdateRequest(
        @NotNull WebhookStatus status
) {
}
