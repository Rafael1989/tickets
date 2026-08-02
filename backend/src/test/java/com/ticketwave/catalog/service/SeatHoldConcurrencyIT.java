package com.ticketwave.catalog.service;

import com.ticketwave.AbstractIntegrationTest;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.model.RouteType;
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
 * see AbstractIntegrationTest for connection/isolation details.
 */
class SeatHoldConcurrencyIT extends AbstractIntegrationTest {

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
        // Distinct customers, not the same user repeated: holdSeat treats a
        // fresh hold by the seat's own current holder as an idempotent
        // reaffirm (not a conflict), so racing with a single shared user
        // would let every attempt "succeed" and defeat the point of this test.
        List<User> customers = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            customers.add(userRepository.save(User.builder()
                    .username("customer-concurrency-" + i)
                    .email("customer-concurrency-" + i + "@example.com")
                    .passwordHash("hash")
                    .role(UserRole.CUSTOMER)
                    .build()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            User customer = customers.get(i);
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    seatHoldService.holdSeat(seat.getId(), customer);
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
