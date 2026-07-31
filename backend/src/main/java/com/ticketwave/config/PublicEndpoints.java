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
     */
    public static final String[] CATALOG = {
            "/api/search",
            "/api/schedules/**"
    };

    public static List<String> all() {
        return Stream.concat(Arrays.stream(AUTH), Arrays.stream(CATALOG)).toList();
    }
}
