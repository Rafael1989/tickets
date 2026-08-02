package com.ticketwave.partner.dto;

import com.ticketwave.partner.entity.WebhookStatus;

import java.time.Instant;

/** Never carries the signing secret — see PartnerWebhookIssuedResponse for the one-time exception at registration. */
public record PartnerWebhookResponse(
        Long id,
        Long partnerId,
        String url,
        String eventType,
        WebhookStatus status,
        Instant createdAt
) {
}
