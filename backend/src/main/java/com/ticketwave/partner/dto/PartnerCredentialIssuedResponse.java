package com.ticketwave.partner.dto;

import java.time.Instant;

/**
 * Returned only from the issue-credential call. clientSecret is the raw
 * plaintext value — it is never persisted and never retrievable again after
 * this response, so the caller (an admin, relaying it to the partner) must
 * capture it now.
 */
public record PartnerCredentialIssuedResponse(
        Long id,
        Long partnerId,
        String clientId,
        String clientSecret,
        Instant createdAt
) {
}
