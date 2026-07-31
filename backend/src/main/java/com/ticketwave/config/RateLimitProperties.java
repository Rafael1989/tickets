package com.ticketwave.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token-bucket limit applied per client (by IP) to the public, unauthenticated
 * endpoints (register, login, search, schedule browsing) — these are the
 * only endpoints with no natural per-user accounting, so they're the ones
 * most exposed to brute-forcing or scraping.
 */
@ConfigurationProperties(prefix = "ticketwave.rate-limit")
public record RateLimitProperties(
        int requestsPerWindow,
        long windowSeconds
) {
}
