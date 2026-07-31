package com.ticketwave;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application context against a real PostgreSQL instance
 * (Liquibase runs its changelog on it too), matching the project's rule of
 * testing against a real database rather than H2. Requires a Docker daemon
 * reachable by Testcontainers.
 */
@Testcontainers
@SpringBootTest
class TicketwaveApplicationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ticketwave.jwt.secret", () -> "test-only-secret-key-at-least-32-bytes-long");
    }

    @Test
    void contextLoads() {
    }
}
