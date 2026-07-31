package com.ticketwave.catalog.service;

import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.SeatUnavailableException;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserRole;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the pessimistic row lock in SeatRepository.findByIdForUpdate
 * actually serializes concurrent hold attempts on the same seat, rather than
 * trusting the annotation alone. Needs a real transactional service bean
 * (not a mock), so this is a full @SpringBootTest against real PostgreSQL —
 * requires a Docker daemon reachable by Testcontainers.
 */
@SpringBootTest
@Testcontainers
class SeatHoldConcurrencyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ticketwave.jwt.secret", () -> "test-only-secret-key-at-least-32-bytes-long");
    }

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    void holdSeat_underConcurrentRequests_onlyOneAttemptSucceeds() throws Exception {
        User operator = userRepository.save(User.builder()
                .username("operator-concurrency")
                .email("operator-concurrency@example.com")
                .passwordHash("hash")
                .role(UserRole.OPERATOR)
                .build());
        Route route = routeRepository.save(Route.builder()
                .operator(operator)
                .type(RouteType.BUS)
                .origin("A")
                .destination("B")
                .build());
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .route(route)
                .departureTime(Instant.now().plusSeconds(3600))
                .arrivalTime(Instant.now().plusSeconds(7200))
                .baseFare(new BigDecimal("10.00"))
                .currency("USD")
                .status(ScheduleStatus.SCHEDULED)
                .build());
        Seat seat = seatRepository.save(Seat.builder()
                .schedule(schedule)
                .seatNumber("1A")
                .seatClass("economy")
                .status(SeatStatus.AVAILABLE)
                .priceModifier(new BigDecimal("1.000"))
                .build());

        int attempts = 8;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    seatHoldService.holdSeat(seat.getId());
                    return true;
                } catch (SeatUnavailableException ex) {
                    return false;
                }
            }));
        }

        ready.await();
        start.countDown();

        long successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }
        executor.shutdown();

        assertThat(successCount).isEqualTo(1);

        Seat reloaded = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SeatStatus.HELD);
    }
}
