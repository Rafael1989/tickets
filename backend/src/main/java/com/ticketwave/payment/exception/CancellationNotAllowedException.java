package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public class CancellationNotAllowedException extends TicketwaveException {

    public CancellationNotAllowedException(Long bookingId, Instant departureTime) {
        super(HttpStatus.CONFLICT, "CANCELLATION_NOT_ALLOWED",
                "Booking " + bookingId + " cannot be cancelled this close to its departure at " + departureTime);
    }
}
