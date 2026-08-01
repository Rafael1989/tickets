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
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently INITIATED or FAILED
     * @throws com.ticketwave.payment.exception.PaymentAmountMismatchException if the amount doesn't match the booking's total
     */
    PaymentResponse recordPayment(Long bookingId, PaymentRequest request);
}
