package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

/**
 * A booking keeps its CONFIRMED status while a cancellation request is under
 * support review, so the booking's own status can't stop a customer from
 * submitting the same request twice (each of which would otherwise create its
 * own PENDING refund, and approving both would pay the fare back more than
 * once).
 */
public class RefundAlreadyPendingException extends TicketwaveException {

    public RefundAlreadyPendingException(Long bookingId) {
        super(HttpStatus.CONFLICT, "REFUND_ALREADY_PENDING",
                "Booking " + bookingId + " already has a refund request awaiting review");
    }
}
