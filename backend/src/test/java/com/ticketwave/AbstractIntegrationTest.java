package com.ticketwave;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

/**
 * Shared base for every full-context integration test against real
 * PostgreSQL (see application-test.yml for the "test" profile — points at
 * ticketwave_test by default). RANDOM_PORT so HTTP-level tests
 * (TestRestTemplate/WebTestClient) and service-layer tests can share one
 * base class.
 *
 * Deliberately NOT @Transactional: that would only roll back work done on
 * the test's own thread. Concurrency tests (SeatHoldConcurrencyIT,
 * PaymentFlowIT's race test) spawn worker threads that need to see setup
 * data as already committed, and real HTTP calls here execute on Tomcat's
 * own request thread, not the test thread - both would silently break
 * under transactional rollback. Isolation instead comes from each test
 * using unique data (random suffixes/UUIDs), the same pattern the existing
 * IT suite already relies on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * Per-invocation unique token for any value under a UNIQUE constraint
     * (username, email, PNR-adjacent references, ...).
     *
     * Not optional bookkeeping: with no Testcontainers isolation and no
     * transactional rollback (see this class's own Javadoc), rows written by
     * one run survive into the next. A hardcoded fixture username therefore
     * passes exactly once against a fresh database and fails with
     * "duplicate key value violates unique constraint" on every run after
     * that - which is precisely how RefundFlowIT broke, taking all 8 of its
     * tests down at setup and turning `mvn verify` into a one-shot command.
     *
     * Prefer this over a class-level constant: two tests in the same class
     * must not collide with each other either.
     */
    protected static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
