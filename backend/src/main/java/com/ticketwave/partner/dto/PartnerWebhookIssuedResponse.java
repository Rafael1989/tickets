package com.ticketwave.partner.dto;

import java.time.Instant;

/**
 * Returned only from the register-webhook call. secret is the raw signing
 * key used to compute each delivery's X-TicketWave-Signature header — never
 * retrievable again after this response.
 */
public record PartnerWebhookIssuedResponse(
        Long id,
        Long partnerId,
        String url,
        String secret,
        String eventType,
        Instant createdAt
) {
}
