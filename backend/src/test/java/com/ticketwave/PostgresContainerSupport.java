package com.ticketwave;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL container for the whole test JVM.
 *
 * Deliberately a hand-rolled singleton rather than @Testcontainers +
 * @Container: those start (and stop) a container per test class, which for
 * this suite means eight containers per `mvn verify` and eight Liquibase runs,
 * buying no isolation the tests actually need. Started once in a static
 * initializer and never stopped explicitly — Testcontainers' Ryuk sidecar
 * removes it when the JVM exits, including after a crash or a cancelled CI job.
 *
 * Pinned to postgres:16, the same image docker-compose.yml and the e2e job
 * use, so a green local run means the same thing a green CI run does.
 *
 * Two entry points because the suite has two bootstrap styles that cannot
 * share a base class: {@link AbstractIntegrationTest} (@SpringBootTest) and
 * ScheduleSpecificationsIT (@DataJpaTest). Both call
 * {@link #registerDatasourceProperties(DynamicPropertyRegistry)}.
 */
public final class PostgresContainerSupport {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("ticketwave_test")
            .withUsername("postgres")
            .withPassword("root");

    static {
        POSTGRES.start();
    }

    private PostgresContainerSupport() {
    }

    /**
     * Spring's liquibase url/user/password are placeholders over
     * spring.datasource.* (see application.yml), so registering the datasource
     * alone is enough to point migrations at the container too.
     */
    public static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
