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
     * POST-only: the partner OAuth2 client-credentials token endpoint — no
     * bearer token exists yet at the point a partner is exchanging its
     * client_id/client_secret for one.
     */
    public static final String[] PARTNER_AUTH = {
            "/api/oauth/token"
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

    /**
     * GET-only: guest "find my booking" lookup by PNR + the email on the
     * booking, as a second factor — distinct from GET /api/bookings/pnr/{pnr}
     * (support/admin only, no second factor needed since the caller is
     * already an authenticated staff member).
     */
    public static final String[] BOOKING_LOOKUP = {
            "/api/bookings/pnr/*/lookup"
    };

    public static List<String> all() {
        return Stream.of(AUTH, PARTNER_AUTH, CATALOG, PROMO, BOOKING_LOOKUP).flatMap(Arrays::stream).toList();
    }
}
