package com.ticketwave.payment.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.config.RefundProperties;
import com.ticketwave.payment.dto.RefundDecision;
import com.ticketwave.payment.dto.RefundQuoteResponse;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.entity.Refund;
import com.ticketwave.payment.entity.RefundStatus;
import com.ticketwave.payment.exception.CancellationNotAllowedException;
import com.ticketwave.payment.exception.InvalidRefundStateException;
import com.ticketwave.payment.exception.PaymentNotFoundException;
import com.ticketwave.payment.exception.RefundNotFoundException;
import com.ticketwave.payment.exception.RefundOverrideAmountExceedsPaymentException;
import com.ticketwave.payment.exception.RefundOverrideReasonRequiredException;
import com.ticketwave.payment.mapper.RefundMapper;
import com.ticketwave.payment.repository.PaymentRepository;
import com.ticketwave.payment.repository.RefundRepository;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookingService bookingService;
    @Mock
    private RefundMapper refundMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private com.ticketwave.partner.service.PartnerWebhookDeliveryService webhookDeliveryService;

    private static final RefundProperties PROPERTIES = new RefundProperties(7, 24, new BigDecimal("0.50"));

    @BeforeEach
    void authenticateAsCustomer() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private RefundServiceImpl newService(RefundProperties properties) {
        return new RefundServiceImpl(refundRepository, paymentRepository, bookingRepository, userRepository,
                bookingService, new RefundPolicyService(properties), refundMapper, auditService, webhookDeliveryService);
    }

    private static Booking booking(long id, BookingStatus status, Schedule schedule) {
        return Booking.builder().id(id).status(status).schedule(schedule).totalAmount(new BigDecimal("100.00")).build();
    }

    private static Schedule scheduleDepartingIn(Duration untilDeparture) {
        return Schedule.builder().id(10L).departureTime(Instant.now().plus(untilDeparture)).build();
    }

    /**
     * booking/schedule/route/operator are wired up (operator with no
     * partner) purely so processRefund's webhook-notification branch has
     * something non-null to navigate through — these tests aren't
     * exercising webhook delivery itself, just mustn't NPE reaching it.
     */
    private static Payment succeededPayment(BigDecimal amount) {
        User operator = User.builder().id(99L).username("operator-webhook-test").build();
        Route route = Route.builder().id(1L).operator(operator).build();
        Schedule schedule = Schedule.builder().id(50L).route(route).build();
        Booking booking = Booking.builder().id(500L).schedule(schedule).pnr("TEST01").build();
        return Payment.builder().id(1L).amount(amount).status(PaymentStatus.SUCCEEDED).booking(booking).build();
    }

    @Test
    void initiateRefund_whenFarFromDeparture_appliesFullRefundAndCancelsBooking() {
        RefundServiceImpl service = newService(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(Duration.ofDays(10));
        Booking booking = booking(500L, BookingStatus.CONFIRMED, schedule);
        Payment payment = succeededPayment(new BigDecimal("100.00"));
        Payment failedAttempt = Payment.builder().id(2L).amount(new BigDecimal("100.00")).status(PaymentStatus.FAILED).build();

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        // A prior failed attempt alongside the successful one exercises the
        // filter's false branch (status != SUCCEEDED), not just the true one.
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(failedAttempt, payment));
        given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));
        given(refundMapper.toResponse(any(Refund.class))).willAnswer(inv -> {
            Refund r = inv.getArgument(0);
            return new RefundResponse(null, 1L, r.getAmount(), r.getPolicyCode(), r.getStatus(), null, null, null, null);
        });

        RefundResponse response = service.initiateRefund(500L);

        assertThat(response.amount()).isEqualByComparingTo("100.00");
        assertThat(response.policyCode()).isEqualTo("FULL_REFUND");
        assertThat(response.status()).isEqualTo(RefundStatus.PENDING);
        verify(bookingService).cancelBooking(500L);
        verify(auditService).record("alice", "REFUND_INITIATED", "REFUND", null,
                "bookingId=500 policy=FULL_REFUND amount=100.00");
    }

    @Test
    void initiateRefund_withinPartialWindow_appliesProratedRefund() {
        RefundServiceImpl service = newService(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(Duration.ofHours(48)); // < 7 days, >= 24 hours
        Booking booking = booking(500L, BookingStatus.CONFIRMED, schedule);
        Payment payment = succeededPayment(new BigDecimal("100.00"));

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));
        given(refundRepository.save(any(Refund.class))).willAnswer(inv -> inv.getArgument(0));
        given(refundMapper.toResponse(any(Refund.class))).willAnswer(inv -> {
            Refund r = inv.getArgument(0);
            return new RefundResponse(null, 1L, r.getAmount(), r.getPolicyCode(), r.getStatus(), null, null, null, null);
        });

        RefundResponse response = service.initiateRefund(500L);

        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.policyCode()).isEqualTo("PARTIAL_REFUND");
        verify(auditService).record(eq("alice"), eq("REFUND_INITIATED"), eq("REFUND"), any(),
                contains("policy=PARTIAL_REFUND"));
    }

    @Test
    void initiateRefund_tooCloseToDeparture_throwsCancellationNotAllowedAndNeverTouchesBookingOrRefund() {
        RefundServiceImpl service = newService(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(Duration.ofHours(2)); // < 24 hours
        Booking booking = booking(500L, BookingStatus.CONFIRMED, schedule);
        Payment payment = succeededPayment(new BigDecimal("100.00"));

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));

        assertThatThrownBy(() -> service.initiateRefund(500L))
                .isInstanceOf(CancellationNotAllowedException.class);

        verify(refundRepository, never()).save(any());
        verify(bookingService, never()).cancelBooking(any());
    }

    @Test
    void initiateRefund_whenDepartureAlreadyInThePast_throwsCancellationNotAllowedException() {
        // Distinct throw site from the "too close but not yet departed"
        // case above — a CONFIRMED booking whose schedule has already
        // happened (e.g. never reconciled) must still be blocked, not
        // treated as eligible for a "0 hours away" partial refund.
        RefundServiceImpl service = newService(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(Duration.ofHours(-2));
        Booking booking = booking(500L, BookingStatus.CONFIRMED, schedule);
        Payment payment = succeededPayment(new BigDecimal("100.00"));

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));

        assertThatThrownBy(() -> service.initiateRefund(500L))
                .isInstanceOf(CancellationNotAllowedException.class);

        verify(refundRepository, never()).save(any());
        verify(bookingService, never()).cancelBooking(any());
    }

    @Test
    void initiateRefund_whenBookingMissing_throwsBookingNotFoundException() {
        RefundServiceImpl service = newService(PROPERTIES);
        given(bookingRepository.findById(500L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateRefund(500L))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void initiateRefund_whenBookingNotConfirmed_throwsInvalidBookingStateException() {
        RefundServiceImpl service = newService(PROPERTIES);
        Booking booking = booking(500L, BookingStatus.INITIATED, scheduleDepartingIn(Duration.ofDays(10)));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.initiateRefund(500L))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void initiateRefund_whenNoSuccessfulPaymentExists_throwsPaymentNotFoundException() {
        RefundServiceImpl service = newService(PROPERTIES);
        Booking booking = booking(500L, BookingStatus.CONFIRMED, scheduleDepartingIn(Duration.ofDays(10)));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of());

        assertThatThrownBy(() -> service.initiateRefund(500L))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void listRefundsForBooking_whenFound_returnsNewestFirstMapped() {
        RefundServiceImpl service = newService(PROPERTIES);
        Refund refund1 = Refund.builder().id(1L).amount(new BigDecimal("50.00")).policyCode("PARTIAL_REFUND")
                .status(RefundStatus.PROCESSED).build();
        Refund refund2 = Refund.builder().id(2L).amount(new BigDecimal("20.00")).policyCode("RESCHEDULE_CREDIT")
                .status(RefundStatus.PENDING).build();

        given(bookingRepository.existsById(500L)).willReturn(true);
        given(refundRepository.findByPaymentBookingIdOrderByIdDesc(500L)).willReturn(List.of(refund2, refund1));
        given(refundMapper.toResponse(refund2)).willReturn(
                new RefundResponse(2L, 1L, refund2.getAmount(), "RESCHEDULE_CREDIT", RefundStatus.PENDING, null, null, null, null));
        given(refundMapper.toResponse(refund1)).willReturn(
                new RefundResponse(1L, 1L, refund1.getAmount(), "PARTIAL_REFUND", RefundStatus.PROCESSED, 9L, Instant.now(), null, null));

        List<RefundResponse> refunds = service.listRefundsForBooking(500L);

        assertThat(refunds).extracting(RefundResponse::id).containsExactly(2L, 1L);
    }

    @Test
    void listRefundsForBooking_whenBookingMissing_throwsBookingNotFoundException() {
        RefundServiceImpl service = newService(PROPERTIES);
        given(bookingRepository.existsById(500L)).willReturn(false);

        assertThatThrownBy(() -> service.listRefundsForBooking(500L))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void previewRefund_whenFarFromDeparture_returnsEligibleFullQuoteWithoutCancellingOrSaving() {
        RefundServiceImpl service = newService(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(Duration.ofDays(10));
        Booking booking = booking(500L, BookingStatus.CONFIRMED, schedule);
        Payment payment = succeededPayment(new BigDecimal("100.00"));
        payment.setMethod("card");

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));

        RefundQuoteResponse quote = service.previewRefund(500L);

        assertThat(quote.eligible()).isTrue();
        assertThat(quote.fareAmount()).isEqualByComparingTo("100.00");
        assertThat(quote.refundAmount()).isEqualByComparingTo("100.00");
        assertThat(quote.nonRefundableAmount()).isEqualByComparingTo("0.00");
        assertThat(quote.policyCode()).isEqualTo("FULL_REFUND");
        assertThat(quote.paymentMethod()).isEqualTo("card");
        verify(refundRepository, never()).save(any());
        verify(bookingService, never()).cancelBooking(any());
    }

    @Test
    void previewRefund_withinPartialWindow_returnsEligiblePartialQuote() {
        RefundServiceImpl service = newService(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(Duration.ofHours(48));
        Booking booking = booking(500L, BookingStatus.CONFIRMED, schedule);
        Payment payment = succeededPayment(new BigDecimal("100.00"));

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));

        RefundQuoteResponse quote = service.previewRefund(500L);

        assertThat(quote.eligible()).isTrue();
        assertThat(quote.refundAmount()).isEqualByComparingTo("50.00");
        assertThat(quote.nonRefundableAmount()).isEqualByComparingTo("50.00");
        assertThat(quote.policyCode()).isEqualTo("PARTIAL_REFUND");
    }

    @Test
    void previewRefund_tooCloseToDeparture_returnsIneligibleQuoteInsteadOfThrowing() {
        RefundServiceImpl service = newService(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(Duration.ofHours(2));
        Booking booking = booking(500L, BookingStatus.CONFIRMED, schedule);
        Payment payment = succeededPayment(new BigDecimal("100.00"));

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));

        RefundQuoteResponse quote = service.previewRefund(500L);

        assertThat(quote.eligible()).isFalse();
        assertThat(quote.policyCode()).isNull();
        assertThat(quote.refundAmount()).isEqualByComparingTo("0.00");
        assertThat(quote.nonRefundableAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void previewRefund_whenBookingNotConfirmed_throwsInvalidBookingStateException() {
        RefundServiceImpl service = newService(PROPERTIES);
        Booking booking = booking(500L, BookingStatus.INITIATED, scheduleDepartingIn(Duration.ofDays(10)));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.previewRefund(500L))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void previewRefund_whenNoSuccessfulPaymentExists_throwsPaymentNotFoundException() {
        RefundServiceImpl service = newService(PROPERTIES);
        Booking booking = booking(500L, BookingStatus.CONFIRMED, scheduleDepartingIn(Duration.ofDays(10)));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of());

        assertThatThrownBy(() -> service.previewRefund(500L))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void previewRefund_whenBookingMissing_throwsBookingNotFoundException() {
        RefundServiceImpl service = newService(PROPERTIES);
        given(bookingRepository.findById(500L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewRefund(500L))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void processRefund_approve_marksProcessedAndRefundsThePayment() {
        RefundServiceImpl service = newService(PROPERTIES);
        Payment payment = succeededPayment(new BigDecimal("100.00"));
        Refund refund = Refund.builder().id(1L).payment(payment).amount(new BigDecimal("100.00"))
                .policyCode("FULL_REFUND").status(RefundStatus.PENDING).build();
        User admin = User.builder().id(9L).username("support1").build();

        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        given(userRepository.findByUsername("support1")).willReturn(Optional.of(admin));
        given(refundMapper.toResponse(refund)).willReturn(
                new RefundResponse(1L, 1L, refund.getAmount(), "FULL_REFUND", RefundStatus.PROCESSED, 9L, Instant.now(), null, null));

        RefundResponse response = service.processRefund(1L, "support1", RefundDecision.APPROVE, null, null);

        assertThat(response.status()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(refund.getProcessedBy()).isEqualTo(admin);
        assertThat(refund.getProcessedAt()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(auditService).record("support1", "REFUND_APPROVED", "REFUND", 1L, "status=PROCESSED");
    }

    @Test
    void processRefund_approve_forRescheduleCredit_leavesPaymentSucceeded() {
        // Unlike a cancellation refund, a RESCHEDULE_CREDIT settles a fare
        // difference on a booking that's still CONFIRMED - the payment keeps
        // backing an active booking (at its already-reduced amount), so
        // approving the credit must not flip it to REFUNDED.
        RefundServiceImpl service = newService(PROPERTIES);
        Payment payment = succeededPayment(new BigDecimal("40.00"));
        Refund refund = Refund.builder().id(1L).payment(payment).amount(new BigDecimal("20.00"))
                .policyCode("RESCHEDULE_CREDIT").status(RefundStatus.PENDING).build();
        User admin = User.builder().id(9L).username("support1").build();

        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        given(userRepository.findByUsername("support1")).willReturn(Optional.of(admin));
        given(refundMapper.toResponse(refund)).willReturn(
                new RefundResponse(1L, 1L, refund.getAmount(), "RESCHEDULE_CREDIT", RefundStatus.PROCESSED, 9L, Instant.now(), null, null));

        service.processRefund(1L, "support1", RefundDecision.APPROVE, null, null);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void processRefund_reject_marksRejectedAndLeavesPaymentUntouched() {
        RefundServiceImpl service = newService(PROPERTIES);
        Payment payment = succeededPayment(new BigDecimal("100.00"));
        Refund refund = Refund.builder().id(1L).payment(payment).amount(new BigDecimal("100.00"))
                .policyCode("FULL_REFUND").status(RefundStatus.PENDING).build();
        User admin = User.builder().id(9L).username("support1").build();

        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        given(userRepository.findByUsername("support1")).willReturn(Optional.of(admin));
        given(refundMapper.toResponse(refund)).willReturn(
                new RefundResponse(1L, 1L, refund.getAmount(), "FULL_REFUND", RefundStatus.REJECTED, 9L, Instant.now(), null, null));

        service.processRefund(1L, "support1", RefundDecision.REJECT, null, null);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED); // unchanged
        verify(auditService).record("support1", "REFUND_REJECTED", "REFUND", 1L, "status=REJECTED");
    }

    @Test
    void processRefund_approveWithOverride_waivesFeeAndRecordsDeltaAndReason() {
        RefundServiceImpl service = newService(PROPERTIES);
        Payment payment = succeededPayment(new BigDecimal("100.00"));
        Refund refund = Refund.builder().id(1L).payment(payment).amount(new BigDecimal("50.00"))
                .policyCode("PARTIAL_REFUND").status(RefundStatus.PENDING).build();
        User admin = User.builder().id(9L).username("support1").build();

        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        given(userRepository.findByUsername("support1")).willReturn(Optional.of(admin));
        given(refundMapper.toResponse(refund)).willReturn(
                new RefundResponse(1L, 1L, refund.getAmount(), "PARTIAL_REFUND", RefundStatus.PROCESSED, 9L,
                        Instant.now(), refund.getOverrideDelta(), refund.getOverrideReason()));

        service.processRefund(1L, "support1", RefundDecision.APPROVE, new BigDecimal("100.00"), "Goodwill waiver - repeat customer");

        assertThat(refund.getAmount()).isEqualByComparingTo("100.00");
        assertThat(refund.getOverrideDelta()).isEqualByComparingTo("50.00");
        assertThat(refund.getOverrideReason()).isEqualTo("Goodwill waiver - repeat customer");
        verify(auditService).record("support1", "REFUND_FEE_OVERRIDDEN", "REFUND", 1L,
                "delta=50.00 reason=Goodwill waiver - repeat customer");
        verify(auditService).record("support1", "REFUND_APPROVED", "REFUND", 1L, "status=PROCESSED");
    }

    @Test
    void processRefund_approveWithOverrideAmountButNoReason_throwsRefundOverrideReasonRequiredException() {
        RefundServiceImpl service = newService(PROPERTIES);
        Payment payment = succeededPayment(new BigDecimal("100.00"));
        Refund refund = Refund.builder().id(1L).payment(payment).amount(new BigDecimal("50.00"))
                .policyCode("PARTIAL_REFUND").status(RefundStatus.PENDING).build();
        User admin = User.builder().id(9L).username("support1").build();

        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        given(userRepository.findByUsername("support1")).willReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.processRefund(1L, "support1", RefundDecision.APPROVE, new BigDecimal("100.00"), " "))
                .isInstanceOf(RefundOverrideReasonRequiredException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void processRefund_approveWithOverrideAmountExceedingPayment_throwsRefundOverrideAmountExceedsPaymentException() {
        RefundServiceImpl service = newService(PROPERTIES);
        Payment payment = succeededPayment(new BigDecimal("100.00"));
        Refund refund = Refund.builder().id(1L).payment(payment).amount(new BigDecimal("50.00"))
                .policyCode("PARTIAL_REFUND").status(RefundStatus.PENDING).build();
        User admin = User.builder().id(9L).username("support1").build();

        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        given(userRepository.findByUsername("support1")).willReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.processRefund(1L, "support1", RefundDecision.APPROVE, new BigDecimal("150.00"), "Full waiver"))
                .isInstanceOf(RefundOverrideAmountExceedsPaymentException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void processRefund_rejectWithOverrideAmount_ignoresOverride() {
        RefundServiceImpl service = newService(PROPERTIES);
        Payment payment = succeededPayment(new BigDecimal("100.00"));
        Refund refund = Refund.builder().id(1L).payment(payment).amount(new BigDecimal("50.00"))
                .policyCode("PARTIAL_REFUND").status(RefundStatus.PENDING).build();
        User admin = User.builder().id(9L).username("support1").build();

        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        given(userRepository.findByUsername("support1")).willReturn(Optional.of(admin));
        given(refundMapper.toResponse(refund)).willReturn(
                new RefundResponse(1L, 1L, refund.getAmount(), "PARTIAL_REFUND", RefundStatus.REJECTED, 9L, Instant.now(), null, null));

        service.processRefund(1L, "support1", RefundDecision.REJECT, new BigDecimal("100.00"), "Should be ignored");

        assertThat(refund.getAmount()).isEqualByComparingTo("50.00");
        assertThat(refund.getOverrideDelta()).isNull();
        assertThat(refund.getOverrideReason()).isNull();
        verify(auditService, never()).record(any(), eq("REFUND_FEE_OVERRIDDEN"), any(), any(), any());
    }

    @Test
    void processRefund_whenRefundMissing_throwsRefundNotFoundException() {
        RefundServiceImpl service = newService(PROPERTIES);
        given(refundRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.processRefund(1L, "support1", RefundDecision.APPROVE, null, null))
                .isInstanceOf(RefundNotFoundException.class);
    }

    @Test
    void processRefund_whenAlreadyProcessed_throwsInvalidRefundStateException() {
        RefundServiceImpl service = newService(PROPERTIES);
        Refund refund = Refund.builder().id(1L).status(RefundStatus.PROCESSED).build();
        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));

        assertThatThrownBy(() -> service.processRefund(1L, "support1", RefundDecision.APPROVE, null, null))
                .isInstanceOf(InvalidRefundStateException.class);
    }

    @Test
    void processRefund_whenProcessorMissing_throwsUserNotFoundException() {
        RefundServiceImpl service = newService(PROPERTIES);
        Refund refund = Refund.builder().id(1L).status(RefundStatus.PENDING).build();
        given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
        given(userRepository.findByUsername("support1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.processRefund(1L, "support1", RefundDecision.APPROVE, null, null))
                .isInstanceOf(UserNotFoundException.class);
    }
}
