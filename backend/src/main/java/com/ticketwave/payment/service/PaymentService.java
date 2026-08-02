package com.ticketwave.payment.service;

import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;

public interface PaymentService {

    /**
     * Attempts a payment for a booking against the simulated gateway
     * (see CardDeclineSimulator) and confirms or fails the booking as a
     * result. Idempotent on request.reference(): replaying the same
     * reference returns the original payment instead of creating a
     * duplicate or re-deciding the outcome again. A booking that was FAILED
     * by a prior declined attempt can be retried through this same method.
     * <p>
     * A card recognized as requiring 3D Secure is neither approved nor
     * declined here: the payment is saved as PENDING_3DS and the booking
     * stays PAYMENT_PROCESSING until {@link #confirmThreeDs} settles it.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently INITIATED or FAILED
     * @throws com.ticketwave.payment.exception.PaymentAmountMismatchException if the amount doesn't match the booking's total
     */
    PaymentResponse recordPayment(Long bookingId, PaymentRequest request);

    /**
     * Settles a PENDING_3DS payment: code must match the simulated
     * authentication code, exactly like a real 3DS challenge either
     * completes or doesn't. On success, confirms the booking; on failure,
     * fails it — same as an ordinary decline.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     * @throws com.ticketwave.payment.exception.PaymentNotFoundException if no such payment exists on this booking
     * @throws com.ticketwave.payment.exception.InvalidPaymentStateException if the payment isn't currently PENDING_3DS
     */
    PaymentResponse confirmThreeDs(Long bookingId, Long paymentId, String code);
}
