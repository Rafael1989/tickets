package com.ticketwave.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnerWebhookRequest(
        @NotBlank @Size(max = 500) String url,
        @NotBlank @Size(max = 50) String eventType
) {
}
