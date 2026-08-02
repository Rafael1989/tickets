package com.ticketwave.booking.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class DuplicateBookingRequestException extends TicketwaveException {

    public DuplicateBookingRequestException(String idempotencyKey) {
        super(HttpStatus.CONFLICT, "DUPLICATE_BOOKING_REQUEST",
                "A booking was already created for idempotency key " + idempotencyKey
                        + "; look it up instead of retrying");
    }
}
