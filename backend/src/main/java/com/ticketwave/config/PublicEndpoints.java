package com.ticketwave.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Single source of truth for which API paths don't require authentication —
 * referenced by both SecurityConfig (to permit them) and the rate-limiting
 * filter (to throttle them), so the two can never drift apart.
 */
public final class PublicEndpoints {

    private PublicEndpoints() {
    }

    /**
     * POST-only: register/login.
     */
    public static final String[] AUTH = {
            "/api/register",
            "/api/login"
    };

    /**
     * GET-only: guest browsing (search, schedule details, seat availability) per US1.
     * Note that /api/schedules/** also covers the seat hold/release endpoints
     * (POST/DELETE .../seats/{id}/hold) — those stay authenticated because
     * this list is wired into SecurityConfig as GET-only permitAll.
     */
    public static final String[] CATALOG = {
            "/api/search",
            "/api/schedules/**"
    };

    /**
     * POST-only: promo code preview — read-only, doesn't require an account.
     */
    public static final String[] PROMO = {
            "/api/promos/validate"
    };

    public static List<String> all() {
        return Stream.of(AUTH, CATALOG, PROMO).flatMap(Arrays::stream).toList();
    }
}
