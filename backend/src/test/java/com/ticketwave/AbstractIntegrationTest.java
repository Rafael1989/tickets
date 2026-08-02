package com.ticketwave;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
}
