package com.ticketwave.partner.dto;

import jakarta.validation.constraints.NotBlank;

/** OAuth2 client-credentials grant, simplified to this app's JSON-only API style rather than form-urlencoded. */
public record PartnerTokenRequest(
        @NotBlank String clientId,
        @NotBlank String clientSecret
) {
}
