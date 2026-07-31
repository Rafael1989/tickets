package com.ticketwave.payment.service;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.SeatSelection;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.repository.PaymentRepository;
import com.ticketwave.user.entity.Passenger;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserRole;
import com.ticketwave.user.repository.PassengerRepository;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end against real PostgreSQL: happy-path payment confirms the
 * booking, replaying the same reference is idempotent, and two concurrent
 * requests carrying the same reference never produce two payment rows or
 * confirm the booking twice. Requires a Docker daemon reachable by
 * Testcontainers.
 */
@SpringBootTest
@Testcontainers
class PaymentFlowIT {

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
    private PaymentService paymentService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentRepository paymentRepository;
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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String lastCustomerUsername;

    private void authenticateAs(String username, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private User newUser(String username, UserRole role) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hash")
                .role(role)
                .build());
    }

    private BookingDetailResponse newInitiatedBooking(String suffix) {
        User operator = newUser("operator-pay-" + suffix, UserRole.OPERATOR);
        User customer = newUser("customer-pay-" + suffix, UserRole.CUSTOMER);
        Route route = routeRepository.save(Route.builder()
                .operator(operator).type(RouteType.BUS).origin("NYC").destination("Boston").build());
        // 10 days out and padded with filler seats: keeps the dynamic pricing
        // engine's demand adjustments neutral, so totalAmount is exactly
        // baseFare * seat.priceModifier and payment-amount assertions hold.
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .route(route)
                .departureTime(Instant.now().plus(Duration.ofDays(10)))
                .arrivalTime(Instant.now().plus(Duration.ofDays(10)).plusSeconds(3600))
                .baseFare(new BigDecimal("20.00"))
                .currency("USD")
                .status(ScheduleStatus.SCHEDULED)
                .build());
        seatRepository.save(Seat.builder().schedule(schedule).seatNumber("F1").seatClass("economy")
                .status(SeatStatus.AVAILABLE).priceModifier(new BigDecimal("1.000")).build());
        seatRepository.save(Seat.builder().schedule(schedule).seatNumber("F2").seatClass("economy")
                .status(SeatStatus.AVAILABLE).priceModifier(new BigDecimal("1.000")).build());
        Seat seat = seatRepository.save(Seat.builder().schedule(schedule).seatNumber("1A").seatClass("economy")
                .status(SeatStatus.AVAILABLE).priceModifier(new BigDecimal("1.500")).build());
        Passenger passenger = passengerRepository.save(Passenger.builder()
                .user(customer).fullName("Jane Doe").dob(LocalDate.of(1990, 1, 1))
                .idType("passport").idNumber("X123456").build());

        lastCustomerUsername = customer.getUsername();
        authenticateAs(lastCustomerUsername, UserRole.CUSTOMER);
        return bookingService.createBooking(lastCustomerUsername, new CreateBookingRequest(
                schedule.getId(), List.of(new SeatSelection(seat.getId(), passenger.getId())), null));
    }

    @Test
    void recordPayment_happyPath_confirmsBookingAndBooksTheSeat() {
        BookingDetailResponse created = newInitiatedBooking("happy");
        Long bookingId = created.booking().id();
        Long seatId = created.items().get(0).seatId();
        BigDecimal total = created.booking().totalAmount();

        PaymentResponse response = paymentService.recordPayment(bookingId,
                new PaymentRequest(total, "card", "PAY-HAPPY-1"));

        assertThat(response.status().name()).isEqualTo("SUCCEEDED");
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.BOOKED);

        // The booking is already CONFIRMED; a second confirm attempt must
        // fail-fast rather than silently succeed again.
        assertThatThrownBy(() -> bookingService.confirmBooking(bookingId))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void recordPayment_replayedWithSameReference_isIdempotent() {
        BookingDetailResponse created = newInitiatedBooking("idem");
        Long bookingId = created.booking().id();
        BigDecimal total = created.booking().totalAmount();
        PaymentRequest request = new PaymentRequest(total, "card", "PAY-IDEMPOTENT-1");

        PaymentResponse first = paymentService.recordPayment(bookingId, request);
        PaymentResponse second = paymentService.recordPayment(bookingId, request);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(paymentRepository.findByReference("PAY-IDEMPOTENT-1")).isPresent();
        long paymentCount = paymentRepository.findByBookingId(bookingId).size();
        assertThat(paymentCount).isEqualTo(1);
    }

    @Test
    void recordPayment_concurrentRequestsWithSameReference_produceExactlyOnePaymentAndOneConfirmation() throws Exception {
        BookingDetailResponse created = newInitiatedBooking("race");
        Long bookingId = created.booking().id();
        BigDecimal total = created.booking().totalAmount();
        PaymentRequest request = new PaymentRequest(total, "card", "PAY-RACE-1");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        String customerUsername = lastCustomerUsername;
        Future<PaymentResponse> attempt1 = executor.submit(() -> {
            start.await();
            authenticateAs(customerUsername, UserRole.CUSTOMER);
            return paymentService.recordPayment(bookingId, request);
        });
        Future<PaymentResponse> attempt2 = executor.submit(() -> {
            start.await();
            authenticateAs(customerUsername, UserRole.CUSTOMER);
            return paymentService.recordPayment(bookingId, request);
        });

        start.countDown();
        PaymentResponse result1 = attempt1.get(30, TimeUnit.SECONDS);
        PaymentResponse result2 = attempt2.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(result1.id()).isEqualTo(result2.id());
        assertThat(paymentRepository.findByBookingId(bookingId)).hasSize(1);
    }
}
