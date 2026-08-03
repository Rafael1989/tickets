package com.ticketwave.payment.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.BookingResponse;
import com.ticketwave.booking.dto.RescheduleQuoteResponse;
import com.ticketwave.booking.dto.RescheduleRequest;
import com.ticketwave.booking.dto.SeatSelection;
import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.config.RefundProperties;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.exception.CancellationNotAllowedException;
import com.ticketwave.payment.exception.FareDifferenceDeclinedException;
import com.ticketwave.payment.exception.FareDifferencePaymentRequiredException;
import com.ticketwave.payment.exception.PaymentNotFoundException;
import com.ticketwave.payment.repository.PaymentRepository;
import com.ticketwave.payment.repository.RefundRepository;
import com.ticketwave.pricing.dto.PromoCodeApplication;
import com.ticketwave.pricing.entity.PromoCode;
import com.ticketwave.pricing.service.PricingService;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RescheduleServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private PricingService pricingService;
    @Mock
    private BookingService bookingService;
    @Mock
    private AuditService auditService;

    private static final RefundProperties PROPERTIES = new RefundProperties(7, 24, new BigDecimal("0.50"));
    private static final String APPROVED_CARD = "4242424242424242";
    private static final String DECLINED_CARD = "4000000000000002";

    private RescheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RescheduleServiceImpl(bookingRepository, paymentRepository, refundRepository, scheduleRepository,
                seatRepository, pricingService, new RefundPolicyService(PROPERTIES), new CardDeclineSimulator(),
                bookingService, auditService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Booking booking(BookingStatus status, Duration untilDeparture, BigDecimal totalAmount) {
        Schedule schedule = Schedule.builder().id(10L).departureTime(Instant.now().plus(untilDeparture)).build();
        return Booking.builder().id(500L).status(status).schedule(schedule).totalAmount(totalAmount).build();
    }

    private static Seat seat(long id, BigDecimal priceModifier) {
        return Seat.builder().id(id).priceModifier(priceModifier).build();
    }

    private static Payment succeededPayment(BigDecimal amount) {
        return Payment.builder().id(1L).amount(amount).status(PaymentStatus.SUCCEEDED).build();
    }

    private static RescheduleRequest requestWithPayment(String method, String reference, String cardNumber) {
        return new RescheduleRequest(20L, List.of(new SeatSelection(5L, 100L)), method, reference, cardNumber);
    }

    private BookingDetailResponse detailWithTotal(BigDecimal total) {
        return new BookingDetailResponse(
                new BookingResponse(500L, 1L, 20L, "ABC234", Instant.now(), BookingStatus.CONFIRMED, total, null),
                List.of());
    }

    // --- previewReschedule ---

    @Test
    void previewReschedule_forInitiatedBooking_isAlwaysEligibleAndNeverRequiresPayment() {
        Booking booking = booking(BookingStatus.INITIATED, Duration.ofHours(2), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(Schedule.builder().id(20L).build()));
        Seat seat = seat(5L, BigDecimal.ONE);
        given(seatRepository.findAllById(List.of(5L))).willReturn(List.of(seat));
        given(pricingService.calculateSeatFare(any(), eq(seat))).willReturn(new BigDecimal("65.00"));

        RescheduleQuoteResponse quote = service.previewReschedule(500L, 20L, List.of(5L));

        assertThat(quote.eligible()).isTrue();
        assertThat(quote.paymentRequired()).isFalse();
        assertThat(quote.fareDifference()).isEqualByComparingTo("15.00");
    }

    @Test
    void previewReschedule_forConfirmedBooking_farFromDeparture_upgrade_requiresPayment() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(Schedule.builder().id(20L).build()));
        Seat seat = seat(5L, BigDecimal.ONE);
        given(seatRepository.findAllById(List.of(5L))).willReturn(List.of(seat));
        given(pricingService.calculateSeatFare(any(), eq(seat))).willReturn(new BigDecimal("65.00"));

        RescheduleQuoteResponse quote = service.previewReschedule(500L, 20L, List.of(5L));

        assertThat(quote.eligible()).isTrue();
        assertThat(quote.paymentRequired()).isTrue();
        assertThat(quote.fareDifference()).isEqualByComparingTo("15.00");
    }

    @Test
    void previewReschedule_forConfirmedBooking_downgrade_doesNotRequirePayment() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(Schedule.builder().id(20L).build()));
        Seat seat = seat(5L, BigDecimal.ONE);
        given(seatRepository.findAllById(List.of(5L))).willReturn(List.of(seat));
        given(pricingService.calculateSeatFare(any(), eq(seat))).willReturn(new BigDecimal("30.00"));

        RescheduleQuoteResponse quote = service.previewReschedule(500L, 20L, List.of(5L));

        assertThat(quote.eligible()).isTrue();
        assertThat(quote.paymentRequired()).isFalse();
        assertThat(quote.fareDifference()).isEqualByComparingTo("-20.00");
    }

    @Test
    void previewReschedule_forConfirmedBooking_tooCloseToDeparture_isIneligible() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofHours(2), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(Schedule.builder().id(20L).build()));
        Seat seat = seat(5L, BigDecimal.ONE);
        given(seatRepository.findAllById(List.of(5L))).willReturn(List.of(seat));
        given(pricingService.calculateSeatFare(any(), eq(seat))).willReturn(new BigDecimal("65.00"));

        RescheduleQuoteResponse quote = service.previewReschedule(500L, 20L, List.of(5L));

        assertThat(quote.eligible()).isFalse();
        assertThat(quote.paymentRequired()).isFalse();
    }

    @Test
    void previewReschedule_whenBookingCancelled_throwsInvalidBookingStateException() {
        Booking booking = booking(BookingStatus.CANCELLED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.previewReschedule(500L, 20L, List.of(5L)))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void previewReschedule_whenBookingMissing_throwsBookingNotFoundException() {
        given(bookingRepository.findById(500L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewReschedule(500L, 20L, List.of(5L)))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void previewReschedule_whenScheduleMissing_throwsScheduleNotFoundException() {
        Booking booking = booking(BookingStatus.INITIATED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(scheduleRepository.findById(20L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewReschedule(500L, 20L, List.of(5L)))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void previewReschedule_whenSeatMissing_throwsSeatNotFoundException() {
        Booking booking = booking(BookingStatus.INITIATED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(Schedule.builder().id(20L).build()));
        given(seatRepository.findAllById(List.of(5L))).willReturn(List.of());

        assertThatThrownBy(() -> service.previewReschedule(500L, 20L, List.of(5L)))
                .isInstanceOf(SeatNotFoundException.class);
    }

    // --- reschedule ---

    @Test
    void reschedule_forInitiatedBooking_delegatesDirectlyWithNoPolicyOrBillingChecks() {
        Booking booking = booking(BookingStatus.INITIATED, Duration.ofHours(2), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        BookingDetailResponse expected = detailWithTotal(new BigDecimal("65.00"));
        RescheduleRequest request = requestWithPayment(null, null, null);
        given(bookingService.rescheduleBooking(500L, request)).willReturn(expected);

        BookingDetailResponse result = service.reschedule(500L, request);

        assertThat(result).isEqualTo(expected);
        verify(paymentRepository, never()).findByBookingId(any());
    }

    @Test
    void reschedule_forConfirmedBooking_noFareDifference_touchesNeitherPaymentNorRefund() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        Payment payment = succeededPayment(new BigDecimal("50.00"));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));
        RescheduleRequest request = requestWithPayment(null, null, null);
        given(bookingService.rescheduleBooking(500L, request)).willReturn(detailWithTotal(new BigDecimal("50.00")));

        service.reschedule(500L, request);

        assertThat(payment.getAmount()).isEqualByComparingTo("50.00");
        verify(refundRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void reschedule_forConfirmedBooking_upgradeWithValidPayment_increasesPaymentAmount() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        Payment payment = succeededPayment(new BigDecimal("50.00"));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));
        RescheduleRequest request = requestWithPayment("card", "REF-1", APPROVED_CARD);
        given(bookingService.rescheduleBooking(500L, request)).willReturn(detailWithTotal(new BigDecimal("65.00")));

        service.reschedule(500L, request);

        assertThat(payment.getAmount()).isEqualByComparingTo("65.00");
        verify(auditService).record("alice", "RESCHEDULE_FARE_COLLECTED", "PAYMENT", 1L, "bookingId=500 amount=15.00");
        verify(refundRepository, never()).save(any());
    }

    @Test
    void reschedule_forConfirmedBooking_upgradeWithoutPaymentDetails_throwsAndNeverPersistsAnything() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        Payment payment = succeededPayment(new BigDecimal("50.00"));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));
        RescheduleRequest request = requestWithPayment(null, null, null);
        given(bookingService.rescheduleBooking(500L, request)).willReturn(detailWithTotal(new BigDecimal("65.00")));

        assertThatThrownBy(() -> service.reschedule(500L, request))
                .isInstanceOf(FareDifferencePaymentRequiredException.class);

        assertThat(payment.getAmount()).isEqualByComparingTo("50.00");
        verify(refundRepository, never()).save(any());
    }

    @Test
    void reschedule_forConfirmedBooking_upgradeDeclined_throwsAndLeavesPaymentUntouched() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        Payment payment = succeededPayment(new BigDecimal("50.00"));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));
        RescheduleRequest request = requestWithPayment("card", "REF-1", DECLINED_CARD);
        given(bookingService.rescheduleBooking(500L, request)).willReturn(detailWithTotal(new BigDecimal("65.00")));

        assertThatThrownBy(() -> service.reschedule(500L, request))
                .isInstanceOf(FareDifferenceDeclinedException.class);

        assertThat(payment.getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void reschedule_forConfirmedBooking_downgrade_reducesPaymentAndIssuesPendingCreditRefund() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        Payment payment = succeededPayment(new BigDecimal("50.00"));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));
        RescheduleRequest request = requestWithPayment(null, null, null);
        given(bookingService.rescheduleBooking(500L, request)).willReturn(detailWithTotal(new BigDecimal("30.00")));
        given(refundRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.reschedule(500L, request);

        assertThat(payment.getAmount()).isEqualByComparingTo("30.00");
        verify(refundRepository).save(argThat(refund ->
                refund.getAmount().compareTo(new BigDecimal("20.00")) == 0
                        && refund.getPolicyCode().equals("RESCHEDULE_CREDIT")
                        && refund.getStatus().name().equals("PENDING")));
        verify(auditService).record(eq("alice"), eq("RESCHEDULE_CREDIT_ISSUED"), eq("REFUND"), any(), eq("bookingId=500 amount=20.00"));
    }

    @Test
    void reschedule_forConfirmedBooking_tooCloseToDeparture_throwsCancellationNotAllowedException() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofHours(2), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        RescheduleRequest request = requestWithPayment(null, null, null);

        assertThatThrownBy(() -> service.reschedule(500L, request))
                .isInstanceOf(CancellationNotAllowedException.class);

        verify(bookingService, never()).rescheduleBooking(any(), any());
    }

    @Test
    void reschedule_forConfirmedBooking_whenNoSuccessfulPaymentExists_throwsPaymentNotFoundException() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of());
        RescheduleRequest request = requestWithPayment(null, null, null);

        assertThatThrownBy(() -> service.reschedule(500L, request))
                .isInstanceOf(PaymentNotFoundException.class);

        verify(bookingService, never()).rescheduleBooking(any(), any());
    }

    @Test
    void reschedule_whenBookingCancelled_throwsInvalidBookingStateException() {
        Booking booking = booking(BookingStatus.CANCELLED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        RescheduleRequest request = requestWithPayment(null, null, null);

        assertThatThrownBy(() -> service.reschedule(500L, request))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void previewReschedule_whenBookingHasPromoCode_subtractsTheDiscountFromTheNewSubtotal() {
        Booking booking = booking(BookingStatus.INITIATED, Duration.ofDays(10), new BigDecimal("50.00"));
        PromoCode promo = PromoCode.builder().id(7L).code("SAVE10").build();
        booking.setPromoCode(promo);
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(Schedule.builder().id(20L).build()));
        Seat seat = seat(5L, BigDecimal.ONE);
        given(seatRepository.findAllById(List.of(5L))).willReturn(List.of(seat));
        given(pricingService.calculateSeatFare(any(), eq(seat))).willReturn(new BigDecimal("65.00"));
        given(pricingService.previewPromoCode("SAVE10", new BigDecimal("65.00")))
                .willReturn(new PromoCodeApplication(promo, new BigDecimal("10.00")));

        RescheduleQuoteResponse quote = service.previewReschedule(500L, 20L, List.of(5L));

        // 65.00 subtotal - 10.00 discount = 55.00, so the delta against the
        // original 50.00 is 5.00 and not the undiscounted 15.00. A booking's
        // promo code has to survive a reschedule, or rebooking silently
        // revokes a discount the customer already earned.
        assertThat(quote.newTotal()).isEqualByComparingTo("55.00");
        assertThat(quote.fareDifference()).isEqualByComparingTo("5.00");
    }

    @Test
    void previewReschedule_whenOnlySomeSeatsExist_reportsTheMissingOne() {
        Booking booking = booking(BookingStatus.INITIATED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(Schedule.builder().id(20L).build()));
        // Seat 5 resolves, seat 6 does not: the partial case, distinct from the
        // findAllById-returns-nothing case above. It is the one that exercises
        // the id-diffing rather than just the size mismatch.
        given(seatRepository.findAllById(List.of(5L, 6L))).willReturn(List.of(seat(5L, BigDecimal.ONE)));

        assertThatThrownBy(() -> service.previewReschedule(500L, 20L, List.of(5L, 6L)))
                .isInstanceOf(SeatNotFoundException.class)
                .hasMessageContaining("6");
    }

    @Test
    void reschedule_whenBookingHasBothAFailedAndASucceededPayment_billsAgainstTheSucceededOne() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        Payment failed = Payment.builder().id(9L).amount(new BigDecimal("50.00")).status(PaymentStatus.FAILED).build();
        Payment succeeded = succeededPayment(new BigDecimal("50.00"));
        // A retried checkout leaves the failed attempt in the table. Billing
        // the fare difference against it would adjust a payment that never
        // took any money.
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(failed, succeeded));
        RescheduleRequest request = requestWithPayment("card", "REF-1", APPROVED_CARD);
        given(bookingService.rescheduleBooking(500L, request)).willReturn(detailWithTotal(new BigDecimal("65.00")));

        service.reschedule(500L, request);

        assertThat(succeeded.getAmount()).isEqualByComparingTo("65.00");
        assertThat(failed.getAmount()).isEqualByComparingTo("50.00");
        verify(auditService).record("alice", "RESCHEDULE_FARE_COLLECTED", "PAYMENT", 1L, "bookingId=500 amount=15.00");
    }

    @Test
    void reschedule_forConfirmedBooking_upgradeWithBlankPaymentReference_throwsFareDifferencePaymentRequiredException() {
        Booking booking = booking(BookingStatus.CONFIRMED, Duration.ofDays(10), new BigDecimal("50.00"));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        Payment payment = succeededPayment(new BigDecimal("50.00"));
        given(paymentRepository.findByBookingId(500L)).willReturn(List.of(payment));
        // Present-but-empty, not null: a form that submits "" for an untouched
        // field must be rejected exactly like a missing field, or an upgrade
        // gets applied with no payment reference to reconcile against.
        RescheduleRequest request = requestWithPayment("card", "   ", APPROVED_CARD);
        given(bookingService.rescheduleBooking(500L, request)).willReturn(detailWithTotal(new BigDecimal("65.00")));

        assertThatThrownBy(() -> service.reschedule(500L, request))
                .isInstanceOf(FareDifferencePaymentRequiredException.class);

        assertThat(payment.getAmount()).isEqualByComparingTo("50.00");
    }
}
