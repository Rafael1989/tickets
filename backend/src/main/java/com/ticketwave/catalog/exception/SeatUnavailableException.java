package com.ticketwave.catalog.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class SeatUnavailableException extends TicketwaveException {

    public SeatUnavailableException(Long seatId) {
        super(HttpStatus.CONFLICT, "SEAT_UNAVAILABLE", "Seat " + seatId + " is not available to hold");
    }
}
