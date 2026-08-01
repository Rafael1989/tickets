package com.ticketwave.payment.service;

import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.exception.PaymentAmountMismatchException;
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
}
