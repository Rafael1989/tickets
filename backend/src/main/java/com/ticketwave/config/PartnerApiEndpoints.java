package com.ticketwave.config;

/**
 * Paths whose rate-limit bucket is keyed by the caller's partner (via its
 * PARTNER_API bearer token's clientId subject) rather than by IP — so one
 * partner's heavy usage can never eat into another partner's quota, or into
 * the shared-by-IP quota the public endpoints use. See
 * RateLimitingFilter.partnerRateLimitKey.
 */
public final class PartnerApiEndpoints {

    private PartnerApiEndpoints() {
    }

    public static final String[] PROTECTED = {
            "/api/partner/**"
    };
}
