package com.ticketwave.payment.service;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.SeatSelection;
import com.ticketwave.booking.entity.BookingStatus;
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
import com.ticketwave.payment.dto.RefundDecision;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.entity.RefundStatus;
import com.ticketwave.payment.exception.CancellationNotAllowedException;
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
import org.springframework.security.access.AccessDeniedException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end against real PostgreSQL: the full refund policy bands (full,
 * partial, blocked), the PENDING -> PROCESSED/REJECTED settlement step, and
 * that processRefund's @PreAuthorize is actually enforced by the real
 * Spring Security method-security proxy, not just present as an annotation.
 * Requires a Docker daemon reachable by Testcontainers.
 */
@SpringBootTest
@Testcontainers
class RefundFlowIT {

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
    private RefundService refundService;
    @Autowired
    private PaymentService paymentService;
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
    @Autowired
    private PaymentRepository paymentRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private User newUser(String username, UserRole role) {
        return userRepository.save(User.builder()
                .username(username).email(username + "@example.com")
                .passwordHash("hash").role(role).build());
    }

    /**
     * Creates and pays for a CONFIRMED booking on a schedule departing
     * `untilDeparture` from now. Padded with filler seats so the dynamic
     * pricing engine's occupancy adjustment stays neutral and the payment
     * amount matches booking.totalAmount() exactly.
     */
    private BookingDetailResponse newConfirmedBooking(String suffix, Duration untilDeparture) {
        User operator = newUser("operator-refund-" + suffix, UserRole.OPERATOR);
        User customer = newUser("customer-refund-" + suffix, UserRole.CUSTOMER);
        Route route = routeRepository.save(Route.builder()
                .operator(operator).type(RouteType.BUS).origin("NYC").destination("Boston").build());
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .route(route)
                .departureTime(Instant.now().plus(untilDeparture))
                .arrivalTime(Instant.now().plus(untilDeparture).plusSeconds(3600))
                .baseFare(new BigDecimal("20.00"))
                .currency("USD")
                .status(ScheduleStatus.SCHEDULED)
                .build());
        seatRepository.save(Seat.builder().schedule(schedule).seatNumber("F1").seatClass("economy")
                .status(SeatStatus.AVAILABLE).priceModifier(new BigDecimal("1.000")).build());
        seatRepository.save(Seat.builder().schedule(schedule).seatNumber("F2").seatClass("economy")
                .status(SeatStatus.AVAILABLE).priceModifier(new BigDecimal("1.000")).build());
        Seat seat = seatRepository.save(Seat.builder().schedule(schedule).seatNumber("1A").seatClass("economy")
                .status(SeatStatus.AVAILABLE).priceModifier(new BigDecimal("1.000")).build());
        Passenger passenger = passengerRepository.save(Passenger.builder()
                .user(customer).fullName("Jane Doe").dob(LocalDate.of(1990, 1, 1))
                .idType("passport").idNumber("X123456").build());

        authenticateAs(customer.getUsername(), UserRole.CUSTOMER);
        BookingDetailResponse created = bookingService.createBooking(customer.getUsername(), new CreateBookingRequest(
                schedule.getId(), List.of(new SeatSelection(seat.getId(), passenger.getId())), null));

        paymentService.recordPayment(created.booking().id(),
                new PaymentRequest(created.booking().totalAmount(), "card", "PAY-REFUND-" + suffix));

        return created;
    }

    @Test
    void initiateRefund_farFromDeparture_fullyRefundsAndCancelsBooking() {
        BookingDetailResponse booking = newConfirmedBooking("full", Duration.ofDays(10));
        Long seatId = booking.items().get(0).seatId();

        RefundResponse refund = refundService.initiateRefund(booking.booking().id());

        assertThat(refund.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.policyCode()).isEqualTo("FULL_REFUND");
        assertThat(refund.amount()).isEqualByComparingTo(booking.booking().totalAmount());
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void initiateRefund_withinPartialWindow_proratesTheRefund() {
        BookingDetailResponse booking = newConfirmedBooking("partial", Duration.ofHours(48));

        RefundResponse refund = refundService.initiateRefund(booking.booking().id());

        assertThat(refund.policyCode()).isEqualTo("PARTIAL_REFUND");
        assertThat(refund.amount()).isEqualByComparingTo(
                booking.booking().totalAmount().multiply(new BigDecimal("0.50")).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void initiateRefund_tooCloseToDeparture_isBlockedAndLeavesBookingConfirmed() {
        BookingDetailResponse booking = newConfirmedBooking("blocked", Duration.ofHours(2));
        Long seatId = booking.items().get(0).seatId();

        assertThatThrownBy(() -> refundService.initiateRefund(booking.booking().id()))
                .isInstanceOf(CancellationNotAllowedException.class);

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    void processRefund_approvedBySupportRole_marksProcessedAndRefundsThePayment() {
        BookingDetailResponse booking = newConfirmedBooking("approve", Duration.ofDays(10));
        User support = newUser("support-processor", UserRole.SUPPORT);
        RefundResponse initiated = refundService.initiateRefund(booking.booking().id());

        authenticateAs(support.getUsername(), UserRole.SUPPORT);
        RefundResponse processed = refundService.processRefund(initiated.id(), support.getUsername(), RefundDecision.APPROVE);

        assertThat(processed.status()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(paymentRepository.findById(initiated.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void processRefund_withCustomerRole_isDeniedByMethodSecurity() {
        BookingDetailResponse booking = newConfirmedBooking("denied", Duration.ofDays(10));
        User support = newUser("support-denied-target", UserRole.SUPPORT);
        RefundResponse initiated = refundService.initiateRefund(booking.booking().id());

        // Still authenticated as the booking's own customer from
        // newConfirmedBooking/initiateRefund above — exactly the role that
        // must not be able to settle a refund.
        assertThatThrownBy(() -> refundService.processRefund(initiated.id(), support.getUsername(), RefundDecision.APPROVE))
                .isInstanceOf(AccessDeniedException.class);
    }
}
