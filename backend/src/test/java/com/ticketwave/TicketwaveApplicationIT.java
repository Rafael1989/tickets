package com.ticketwave;

import org.junit.jupiter.api.Test;

/**
 * Boots the full application context against a real PostgreSQL instance
 * (Liquibase runs its changelog on it too), matching the project's rule of
 * testing against a real database rather than H2. See
 * AbstractIntegrationTest / application-test.yml for connection details
 * (defaults to ticketwave_test on localhost:5432).
 */
class TicketwaveApplicationIT extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
