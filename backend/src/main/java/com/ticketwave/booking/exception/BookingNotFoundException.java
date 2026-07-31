package com.ticketwave.booking.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class BookingNotFoundException extends TicketwaveException {

    public BookingNotFoundException(Long bookingId) {
        super(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking " + bookingId + " was not found");
    }

    public BookingNotFoundException(String pnr) {
        super(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking with PNR " + pnr + " was not found");
    }
}
