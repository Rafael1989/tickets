package com.ticketwave.payment.service;

import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;

public interface PaymentService {

    /**
     * Records a completed payment for a booking and confirms the booking as
     * a result. Idempotent on request.reference(): replaying the same
     * reference returns the original payment instead of creating a
     * duplicate or re-confirming the booking again.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently INITIATED
     * @throws com.ticketwave.payment.exception.PaymentAmountMismatchException if the amount doesn't match the booking's total
     */
    PaymentResponse recordPayment(Long bookingId, PaymentRequest request);
}
