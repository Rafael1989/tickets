package com.ticketwave.partner.dto;

import com.ticketwave.partner.entity.PartnerCredentialStatus;

import java.time.Instant;

/** Never carries the secret — see PartnerCredentialIssuedResponse for the one-time exception at issuance. */
public record PartnerCredentialResponse(
        Long id,
        Long partnerId,
        String clientId,
        PartnerCredentialStatus status,
        Instant createdAt,
        Instant lastUsedAt,
        Instant revokedAt
) {
}
