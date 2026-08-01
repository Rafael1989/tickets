package com.ticketwave.booking.service;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.SeatSelection;
import com.ticketwave.booking.entity.BookingStatus;
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
import com.ticketwave.user.entity.Passenger;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserRole;
import com.ticketwave.user.repository.PassengerRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end against real PostgreSQL: create -> confirm, create -> cancel,
 * and a genuine concurrency check that two bookings racing over the same
 * seats in opposite request order neither deadlock nor double-book. Requires
 * a Docker daemon reachable by Testcontainers.
 */
@SpringBootTest
@Testcontainers
class BookingFlowIT {

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
    private BookingService bookingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PassengerRepository passengerRepository;
    @Autowired
    private RouteRepository routeRepository;
    @Autowired
    private ScheduleRepository scheduleRepository;
    @Autowired
    private SeatRepository seatRepository;

    private User newOperator(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hash")
                .role(UserRole.OPERATOR)
                .build());
    }

    private User newCustomer(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .build());
    }

    private Schedule newSchedule(User operator) {
        Route route = routeRepository.save(Route.builder()
                .operator(operator)
                .type(RouteType.BUS)
                .origin("NYC")
                .destination("Boston")
                .build());
        // 10 days out: outside both the last-minute (24h) and early-bird
        // (30-day) thresholds, so the demand pricing engine applies neither
        // adjustment here — fare assertions below assume a neutral multiplier.
        return scheduleRepository.save(Schedule.builder()
                .route(route)
                .departureTime(Instant.now().plus(java.time.Duration.ofDays(10)))
                .arrivalTime(Instant.now().plus(java.time.Duration.ofDays(10)).plusSeconds(3600))
                .baseFare(new BigDecimal("20.00"))
                .currency("USD")
                .status(ScheduleStatus.SCHEDULED)
                .build());
    }

    private Seat newSeat(Schedule schedule, String seatNumber) {
        return seatRepository.save(Seat.builder()
                .schedule(schedule)
                .seatNumber(seatNumber)
                .seatClass("economy")
                .status(SeatStatus.AVAILABLE)
                .priceModifier(new BigDecimal("1.500"))
                .build());
    }

    /**
     * Pads out the schedule's total seat count so that holding the test's
     * one or two real seats doesn't itself swing occupancy into the
     * high/low demand-pricing bands.
     */
    private void newFillerSeats(Schedule schedule, int count) {
        for (int i = 0; i < count; i++) {
            newSeat(schedule, "FILLER-" + i);
        }
    }

    private Passenger newPassenger(User user) {
        return passengerRepository.save(Passenger.builder()
                .user(user)
                .fullName("Jane Doe")
                .dob(LocalDate.of(1990, 1, 1))
                .idType("passport")
                .idNumber("X123456")
                .build());
    }

    @Test
    void createThenConfirm_movesSeatsToBookedAndBookingToConfirmed() {
        User operator = newOperator("operator-flow-1");
        User customer = newCustomer("customer-flow-1");
        Schedule schedule = newSchedule(operator);
        newFillerSeats(schedule, 2);
        Seat seat = newSeat(schedule, "1A");
        Passenger passenger = newPassenger(customer);

        BookingDetailResponse created = bookingService.createBooking(customer.getUsername(), new CreateBookingRequest(
                schedule.getId(), List.of(new SeatSelection(seat.getId(), passenger.getId())), null));

        assertThat(created.booking().status()).isEqualTo(BookingStatus.INITIATED);
        assertThat(created.booking().totalAmount()).isEqualByComparingTo("30.00");
        assertThat(seatRepository.findById(seat.getId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);

        bookingService.markPaymentProcessing(created.booking().id());
        BookingDetailResponse confirmed = bookingService.confirmBooking(created.booking().id());

        assertThat(confirmed.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(seatRepository.findById(seat.getId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    void createThenCancel_releasesSeatsBackToAvailable() {
        User operator = newOperator("operator-flow-2");
        User customer = newCustomer("customer-flow-2");
        Schedule schedule = newSchedule(operator);
        newFillerSeats(schedule, 2);
        Seat seat = newSeat(schedule, "2B");
        Passenger passenger = newPassenger(customer);

        BookingDetailResponse created = bookingService.createBooking(customer.getUsername(), new CreateBookingRequest(
                schedule.getId(), List.of(new SeatSelection(seat.getId(), passenger.getId())), null));

        bookingService.cancelBooking(created.booking().id());

        assertThat(seatRepository.findById(seat.getId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void concurrentBookingsOverTheSameSeatsInOppositeOrder_neitherDeadlocksNorDoubleBooks() throws Exception {
        User operator = newOperator("operator-flow-3");
        User customerA = newCustomer("customer-flow-3a");
        User customerB = newCustomer("customer-flow-3b");
        Schedule schedule = newSchedule(operator);
        Seat seatX = newSeat(schedule, "3A");
        Seat seatY = newSeat(schedule, "3B");
        Passenger passengerA = newPassenger(customerA);
        Passenger passengerB = newPassenger(customerB);

        CreateBookingRequest requestA = new CreateBookingRequest(schedule.getId(), List.of(
                new SeatSelection(seatY.getId(), passengerA.getId()),
                new SeatSelection(seatX.getId(), passengerA.getId())), null);
        CreateBookingRequest requestB = new CreateBookingRequest(schedule.getId(), List.of(
                new SeatSelection(seatX.getId(), passengerB.getId()),
                new SeatSelection(seatY.getId(), passengerB.getId())), null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> attemptA = executor.submit(() -> {
            start.await();
            try {
                bookingService.createBooking(customerA.getUsername(), requestA);
                return true;
            } catch (SeatUnavailableException ex) {
                return false;
            }
        });
        Future<Boolean> attemptB = executor.submit(() -> {
            start.await();
            try {
                bookingService.createBooking(customerB.getUsername(), requestB);
                return true;
            } catch (SeatUnavailableException ex) {
                return false;
            }
        });

        start.countDown();

        // A generous but bounded timeout: if the lock ordering weren't
        // deadlock-safe, this would hang until the test framework kills it.
        boolean succeededA = attemptA.get(30, TimeUnit.SECONDS);
        boolean succeededB = attemptB.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(succeededA ^ succeededB).as("exactly one of the two racing bookings should succeed").isTrue();
    }
}
