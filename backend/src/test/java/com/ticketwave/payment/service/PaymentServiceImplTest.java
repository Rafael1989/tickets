package com.ticketwave.payment.service;

import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.ledger.service.LedgerService;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.exception.InvalidPaymentStateException;
import com.ticketwave.payment.exception.PaymentAmountMismatchException;
import com.ticketwave.payment.exception.PaymentNotFoundException;
import com.ticketwave.payment.mapper.PaymentMapper;
import com.ticketwave.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingService bookingService;
    @Mock
    private CardDeclineSimulator cardDeclineSimulator;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static Booking booking(long id, BookingStatus status, BigDecimal totalAmount) {
        return Booking.builder().id(id).status(status).totalAmount(totalAmount).build();
    }

    private static Payment payment(long id, String reference) {
        return Payment.builder().id(id).reference(reference).status(PaymentStatus.SUCCEEDED)
                .amount(new BigDecimal("50.00")).paidAt(Instant.now()).build();
    }

    @Test
    void recordPayment_whenReferenceAlreadyUsed_returnsExistingPaymentAndSkipsEverythingElse() {
        Payment existing = payment(1L, "REF-1");
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.of(existing));
        PaymentResponse expectedResponse = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.SUCCEEDED, Instant.now(), null);
        given(paymentMapper.toResponse(existing)).willReturn(expectedResponse);

        PaymentResponse response = paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", null));

        assertThat(response).isEqualTo(expectedResponse);
        verify(bookingRepository, never()).findById(any());
        verify(bookingService, never()).confirmBooking(any());
        verify(paymentRepository, never()).save(any());
        verify(ledgerService, never()).recordPayment(any());
    }

    @Test
    void recordPayment_whenBookingMissing_throwsBookingNotFoundException() {
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", null)))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void recordPayment_whenBookingNotAwaitingPayment_throwsInvalidBookingStateException() {
        Booking booking = booking(500L, BookingStatus.CANCELLED, new BigDecimal("50.00"));
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingService.markPaymentProcessing(500L))
                .willThrow(new InvalidBookingStateException(500L, BookingStatus.CANCELLED, BookingStatus.PAYMENT_PROCESSING));

        assertThatThrownBy(() -> paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", null)))
                .isInstanceOf(InvalidBookingStateException.class);

        verify(paymentRepository, never()).save(any());
    }

    /**
     * Regression test for a race caught by PaymentFlowIT running against real
     * Postgres: two concurrent requests carrying the same reference both pass
     * the initial findByReference pre-check (neither payment row exists yet),
     * then one fully completes (payment saved, booking CONFIRMED) before the
     * other calls markPaymentProcessing - which now sees a CONFIRMED booking
     * and throws InvalidBookingStateException instead of the
     * ObjectOptimisticLockingFailureException the retry loop expects. The
     * loser must recover the winner's payment instead of surfacing that
     * exception to what is, from the caller's perspective, an idempotent retry.
     */
    @Test
    void recordPayment_whenConcurrentSameReferenceRequestAlreadyConfirmedTheBooking_recoversTheWinnersPayment() {
        Booking booking = booking(500L, BookingStatus.CONFIRMED, new BigDecimal("50.00"));
        Payment winningPayment = payment(1L, "REF-1");
        given(paymentRepository.findByReference("REF-1"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(winningPayment));
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingService.markPaymentProcessing(500L))
                .willThrow(new InvalidBookingStateException(500L, BookingStatus.CONFIRMED, BookingStatus.PAYMENT_PROCESSING));
        PaymentResponse expectedResponse = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.SUCCEEDED, Instant.now(), null);
        given(paymentMapper.toResponse(winningPayment)).willReturn(expectedResponse);

        PaymentResponse response = paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", null));

        assertThat(response).isEqualTo(expectedResponse);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void recordPayment_whenMarkPaymentProcessingLosesAnOptimisticLockRaceOnce_retriesAndSucceeds() {
        Booking booking = booking(500L, BookingStatus.INITIATED, new BigDecimal("50.00"));
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingService.markPaymentProcessing(500L))
                .willThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Booking.class, 500L))
                .willReturn(null);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        PaymentResponse expectedResponse = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.SUCCEEDED, Instant.now(), null);
        given(paymentMapper.toResponse(any(Payment.class))).willReturn(expectedResponse);

        PaymentResponse response = paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", "4242424242424242"));

        assertThat(response).isEqualTo(expectedResponse);
        verify(bookingService, org.mockito.Mockito.times(2)).markPaymentProcessing(500L);
    }

    @Test
    void recordPayment_whenMarkPaymentProcessingKeepsLosingTheOptimisticLockRace_rethrowsAfterMaxAttempts() {
        Booking booking = booking(500L, BookingStatus.INITIATED, new BigDecimal("50.00"));
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingService.markPaymentProcessing(500L))
                .willThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Booking.class, 500L));

        assertThatThrownBy(() -> paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", null)))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);

        verify(bookingService, org.mockito.Mockito.times(3)).markPaymentProcessing(500L);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void recordPayment_whenAmountDoesNotMatchBookingTotal_throwsPaymentAmountMismatchExceptionWithoutMarkingProcessing() {
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L))
                .willReturn(Optional.of(booking(500L, BookingStatus.INITIATED, new BigDecimal("50.00"))));

        assertThatThrownBy(() -> paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("49.00"), "card", "REF-1", null)))
                .isInstanceOf(PaymentAmountMismatchException.class);

        verify(paymentRepository, never()).save(any());
        verify(bookingService, never()).markPaymentProcessing(any());
        verify(bookingService, never()).confirmBooking(any());
    }

    @Test
    void recordPayment_happyPath_savesSucceededPaymentAndConfirmsBooking() {
        Booking booking = booking(500L, BookingStatus.INITIATED, new BigDecimal("50.00"));
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        PaymentResponse expectedResponse = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.SUCCEEDED, Instant.now(), null);
        given(paymentMapper.toResponse(any(Payment.class))).willReturn(expectedResponse);

        PaymentResponse response = paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", "4242424242424242"));

        assertThat(response).isEqualTo(expectedResponse);
        verify(bookingService).markPaymentProcessing(500L);
        verify(bookingService).confirmBooking(500L);
        verify(bookingService, never()).failBooking(any());

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(captor.getValue().getPaidAt()).isNotNull();
        assertThat(captor.getValue().getFailureReason()).isNull();
        verify(ledgerService).recordPayment(captor.getValue());
    }

    @Test
    void recordPayment_whenLedgerRecordingFails_stillReturnsTheSuccessfulPaymentInsteadOfPropagating() {
        Booking booking = booking(500L, BookingStatus.INITIATED, new BigDecimal("50.00"));
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        PaymentResponse expectedResponse = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.SUCCEEDED, Instant.now(), null);
        given(paymentMapper.toResponse(any(Payment.class))).willReturn(expectedResponse);
        willThrow(new RuntimeException("permission denied for table ledger_entries"))
                .given(ledgerService).recordPayment(any(Payment.class));

        PaymentResponse response = paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", "4242424242424242"));

        assertThat(response).isEqualTo(expectedResponse);
        verify(bookingService).confirmBooking(500L);
    }

    /**
     * A booking fully discounted by a 100%-off promo code totals 0.00. It still has to go through
     * the payment flow — this API never confirms a booking without a payment behind it — so a
     * zero-amount payment must be accepted and confirm it like any other.
     */
    @Test
    void recordPayment_forAFullyDiscountedZeroTotalBooking_succeedsAndConfirmsTheBooking() {
        Booking booking = booking(500L, BookingStatus.INITIATED, BigDecimal.ZERO);
        given(paymentRepository.findByReference("REF-FREE")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        PaymentResponse expectedResponse = new PaymentResponse(1L, 500L, BigDecimal.ZERO, "pix", "REF-FREE",
                PaymentStatus.SUCCEEDED, Instant.now(), null);
        given(paymentMapper.toResponse(any(Payment.class))).willReturn(expectedResponse);

        PaymentResponse response = paymentService.recordPayment(500L,
                new PaymentRequest(BigDecimal.ZERO, "pix", "REF-FREE", null));

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(bookingService).confirmBooking(500L);
        verify(bookingService, never()).failBooking(any());

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void recordPayment_withKnownDeclineCard_savesFailedPaymentAndFailsBookingWithoutConfirming() {
        Booking booking = booking(500L, BookingStatus.INITIATED, new BigDecimal("50.00"));
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(cardDeclineSimulator.declineReasonFor("4000000000000002"))
                .willReturn(Optional.of("Your card was declined."));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        PaymentResponse expectedResponse = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.FAILED, null, "Your card was declined.");
        given(paymentMapper.toResponse(any(Payment.class))).willReturn(expectedResponse);

        PaymentResponse response = paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", "4000000000000002"));

        assertThat(response).isEqualTo(expectedResponse);
        verify(bookingService).markPaymentProcessing(500L);
        verify(bookingService).failBooking(500L);
        verify(bookingService, never()).confirmBooking(any());

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).isEqualTo("Your card was declined.");
        assertThat(captor.getValue().getPaidAt()).isNull();
        verify(ledgerService, never()).recordPayment(any());
    }

    @Test
    void recordPayment_whenConcurrentDuplicateReferenceInsert_recoversTheWinningPayment() {
        Booking booking = booking(500L, BookingStatus.INITIATED, new BigDecimal("50.00"));
        given(paymentRepository.findByReference("REF-1"))
                .willReturn(Optional.empty()) // first check: no existing payment yet
                .willReturn(Optional.of(payment(1L, "REF-1"))); // recovery read: the concurrent winner
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(paymentRepository.save(any(Payment.class))).willThrow(new DataIntegrityViolationException("duplicate key"));
        PaymentResponse expectedResponse = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.SUCCEEDED, Instant.now(), null);
        given(paymentMapper.toResponse(any(Payment.class))).willReturn(expectedResponse);

        PaymentResponse response = paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", null));

        assertThat(response).isEqualTo(expectedResponse);
        verify(bookingService, never()).confirmBooking(any());
        verify(ledgerService, never()).recordPayment(any());
    }

    @Test
    void recordPayment_whenDuplicateInsertFailsAndRecoveryReadFindsNothing_rethrowsOriginalException() {
        Booking booking = booking(500L, BookingStatus.INITIATED, new BigDecimal("50.00"));
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        DataIntegrityViolationException original = new DataIntegrityViolationException("duplicate key");
        given(paymentRepository.save(any(Payment.class))).willThrow(original);

        assertThatThrownBy(() -> paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", null)))
                .isSameAs(original);
    }

    @Test
    void recordPayment_withThreeDsRequiredCard_savesPending3dsAndLeavesBookingUnsettled() {
        Booking booking = booking(500L, BookingStatus.INITIATED, new BigDecimal("50.00"));
        given(paymentRepository.findByReference("REF-1")).willReturn(Optional.empty());
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(cardDeclineSimulator.requiresThreeDs("4000002500003155")).willReturn(true);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        PaymentResponse expectedResponse = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.PENDING_3DS, null, null);
        given(paymentMapper.toResponse(any(Payment.class))).willReturn(expectedResponse);

        PaymentResponse response = paymentService.recordPayment(500L,
                new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", "4000002500003155"));

        assertThat(response).isEqualTo(expectedResponse);
        verify(bookingService).markPaymentProcessing(500L);
        verify(bookingService, never()).confirmBooking(any());
        verify(bookingService, never()).failBooking(any());
        verify(cardDeclineSimulator, never()).declineReasonFor(any());

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING_3DS);
        assertThat(captor.getValue().getPaidAt()).isNull();
        assertThat(captor.getValue().getFailureReason()).isNull();
        verify(ledgerService, never()).recordPayment(any());
    }

    @Test
    void confirmThreeDs_withValidCode_succeedsPaymentAndConfirmsBooking() {
        Booking booking = Booking.builder().id(500L).build();
        Payment payment = Payment.builder().id(1L).booking(booking).status(PaymentStatus.PENDING_3DS).build();
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(paymentMapper.toResponse(payment)).willReturn(
                new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1", PaymentStatus.SUCCEEDED, Instant.now(), null));

        PaymentResponse response = paymentService.confirmThreeDs(500L, 1L, "123456");

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getPaidAt()).isNotNull();
        verify(bookingService).confirmBooking(500L);
        verify(bookingService, never()).failBooking(any());
        verify(ledgerService).recordPayment(payment);
    }

    /**
     * Regression test for a bug where confirmThreeDs mutated a Payment fetched via
     * paymentRepository.findById(...) without ever calling save(...) on it: findById runs its own
     * short-lived transaction (open-in-view is disabled), so the entity is detached by the time
     * its setters run, and the mutation silently never reaches the database — the response still
     * looked SUCCEEDED while the real row stayed PENDING_3DS forever. Asserting only on `payment`'s
     * in-memory field state (as the test above does) can't catch this, since it's the exact same
     * Java reference either way; only an explicit verify(save(...)) proves persistence was
     * attempted at all.
     */
    @Test
    void confirmThreeDs_withValidCode_persistsTheUpdatedPayment() {
        Booking booking = Booking.builder().id(500L).build();
        Payment payment = Payment.builder().id(1L).booking(booking).status(PaymentStatus.PENDING_3DS).build();
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(paymentMapper.toResponse(any(Payment.class))).willReturn(
                new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1", PaymentStatus.SUCCEEDED, Instant.now(), null));

        paymentService.confirmThreeDs(500L, 1L, "123456");

        var captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(captor.getValue().getPaidAt()).isNotNull();
    }

    @Test
    void confirmThreeDs_whenLedgerRecordingFails_stillSucceedsThePaymentInsteadOfPropagating() {
        Booking booking = Booking.builder().id(500L).build();
        Payment payment = Payment.builder().id(1L).booking(booking).status(PaymentStatus.PENDING_3DS).build();
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(paymentMapper.toResponse(payment)).willReturn(
                new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1", PaymentStatus.SUCCEEDED, Instant.now(), null));
        willThrow(new RuntimeException("permission denied for table ledger_entries"))
                .given(ledgerService).recordPayment(any(Payment.class));

        PaymentResponse response = paymentService.confirmThreeDs(500L, 1L, "123456");

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(bookingService).confirmBooking(500L);
    }

    @Test
    void confirmThreeDs_withWrongCode_failsPaymentAndFailsBooking() {
        Booking booking = Booking.builder().id(500L).build();
        Payment payment = Payment.builder().id(1L).booking(booking).status(PaymentStatus.PENDING_3DS).build();
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(paymentMapper.toResponse(payment)).willReturn(
                new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1", PaymentStatus.FAILED, null, "3D Secure authentication failed."));

        PaymentResponse response = paymentService.confirmThreeDs(500L, 1L, "000000");

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("3D Secure authentication failed.");
        verify(paymentRepository).save(payment);
        verify(bookingService).failBooking(500L);
        verify(bookingService, never()).confirmBooking(any());
        verify(ledgerService, never()).recordPayment(any());
    }

    @Test
    void confirmThreeDs_whenPaymentBelongsToDifferentBooking_throwsPaymentNotFoundException() {
        Booking booking = Booking.builder().id(999L).build();
        Payment payment = Payment.builder().id(1L).booking(booking).status(PaymentStatus.PENDING_3DS).build();
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmThreeDs(500L, 1L, "123456"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void confirmThreeDs_whenPaymentMissing_throwsPaymentNotFoundException() {
        given(paymentRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmThreeDs(500L, 99L, "123456"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void confirmThreeDs_whenPaymentNotPending3ds_throwsInvalidPaymentStateException() {
        Booking booking = Booking.builder().id(500L).build();
        Payment payment = Payment.builder().id(1L).booking(booking).status(PaymentStatus.SUCCEEDED).build();
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmThreeDs(500L, 1L, "123456"))
                .isInstanceOf(InvalidPaymentStateException.class);

        verify(bookingService, never()).confirmBooking(any());
        verify(bookingService, never()).failBooking(any());
    }
}
