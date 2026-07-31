package com.ticketwave.catalog.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class SeatNotFoundException extends TicketwaveException {

    public SeatNotFoundException(Long seatId) {
        super(HttpStatus.NOT_FOUND, "SEAT_NOT_FOUND", "Seat " + seatId + " was not found");
    }
}
