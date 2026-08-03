package com.ticketwave.payment.service;

import com.ticketwave.AbstractIntegrationTest;
import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.SeatSelection;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.model.RouteType;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
 * confirm the booking twice. See AbstractIntegrationTest for
 * connection/isolation details.
 */
class PaymentFlowIT extends AbstractIntegrationTest {

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

    /**
     * Suffixes the username itself rather than trusting callers to: this
     * database is shared across runs and nothing rolls back, so a fixture with
     * a fixed name passes once and then fails forever on uq_users_username.
     */
    private User newUser(String label, UserRole role) {
        String username = label + "-" + uniqueSuffix();
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hash")
                .role(role)
                .build());
    }

    private BookingDetailResponse newInitiatedBooking(String label) {
        User operator = newUser("operator-pay-" + label, UserRole.OPERATOR);
        User customer = newUser("customer-pay-" + label, UserRole.CUSTOMER);
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
                schedule.getId(), List.of(new SeatSelection(seat.getId(), passenger.getId())), null, null));
    }

    @Test
    void recordPayment_happyPath_confirmsBookingAndBooksTheSeat() {
        BookingDetailResponse created = newInitiatedBooking("happy");
        Long bookingId = created.booking().id();
        Long seatId = created.items().get(0).seatId();
        BigDecimal total = created.booking().totalAmount();

        PaymentResponse response = paymentService.recordPayment(bookingId,
                new PaymentRequest(total, "card", "PAY-HAPPY-" + uniqueSuffix(), "4242424242424242"));

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
        // One reference, replayed deliberately - that's the whole point of the
        // test - but unique per run, since payments.reference is UNIQUE and
        // this database outlives the run.
        String reference = "PAY-IDEMPOTENT-" + uniqueSuffix();
        PaymentRequest request = new PaymentRequest(total, "card", reference, "4242424242424242");

        PaymentResponse first = paymentService.recordPayment(bookingId, request);
        PaymentResponse second = paymentService.recordPayment(bookingId, request);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(paymentRepository.findByReference(reference)).isPresent();
        long paymentCount = paymentRepository.findByBookingId(bookingId).size();
        assertThat(paymentCount).isEqualTo(1);
    }

    @Test
    void recordPayment_concurrentRequestsWithSameReference_produceExactlyOnePaymentAndOneConfirmation() throws Exception {
        BookingDetailResponse created = newInitiatedBooking("race");
        Long bookingId = created.booking().id();
        BigDecimal total = created.booking().totalAmount();
        // Both threads share this one reference on purpose - that's the race
        // under test - but it must still be unique across runs.
        PaymentRequest request = new PaymentRequest(total, "card", "PAY-RACE-" + uniqueSuffix(), "4242424242424242");

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

    @Test
    void recordPayment_withDeclineCardThenRetryWithGoodCard_failsThenConfirmsWithoutLosingTheSeat() {
        BookingDetailResponse created = newInitiatedBooking("decline-retry");
        Long bookingId = created.booking().id();
        Long seatId = created.items().get(0).seatId();
        BigDecimal total = created.booking().totalAmount();

        PaymentResponse declined = paymentService.recordPayment(bookingId,
                new PaymentRequest(total, "card", "PAY-DECLINE-" + uniqueSuffix(), "4000000000000002"));

        assertThat(declined.status().name()).isEqualTo("FAILED");
        assertThat(declined.failureReason()).isEqualTo("Your card was declined.");
        // The seat is still held (not released) so the same booking can be retried.
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);

        PaymentResponse retried = paymentService.recordPayment(bookingId,
                new PaymentRequest(total, "card", "PAY-DECLINE-RETRY-" + uniqueSuffix(), "4242424242424242"));

        assertThat(retried.status().name()).isEqualTo("SUCCEEDED");
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(paymentRepository.findByBookingId(bookingId)).hasSize(2);
    }

    /**
     * Regression test for a bug where confirmThreeDs's Payment mutation never survived past the
     * method call: paymentRepository.findById(...) closes its own transaction before returning
     * (open-in-view is disabled), so setting fields on the entity afterward without an explicit
     * save() left the real row at PENDING_3DS forever, even though the response looked SUCCEEDED
     * and the booking really did get confirmed. Re-fetching from the repository in a fresh read
     * (rather than asserting on the same in-memory object confirmThreeDs mutated) is what actually
     * proves the persistence happened — a Mockito-mocked repository can't tell the difference.
     */
    @Test
    void confirmThreeDs_withValidCode_persistsSucceededStatusAcrossAFreshRead() {
        BookingDetailResponse created = newInitiatedBooking("3ds-persist");
        Long bookingId = created.booking().id();
        BigDecimal total = created.booking().totalAmount();

        PaymentResponse pending = paymentService.recordPayment(bookingId,
                new PaymentRequest(total, "card", "PAY-3DS-" + uniqueSuffix(), "4000002500003155"));
        assertThat(pending.status().name()).isEqualTo("PENDING_3DS");

        paymentService.confirmThreeDs(bookingId, pending.id(), "123456");

        var persisted = paymentRepository.findById(pending.id()).orElseThrow();
        assertThat(persisted.getStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(persisted.getPaidAt()).isNotNull();
        assertThat(bookingService.getBooking(bookingId).booking().status().name()).isEqualTo("CONFIRMED");
    }
}
