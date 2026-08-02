package com.ticketwave.partner.dto;

public record PartnerTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
}
