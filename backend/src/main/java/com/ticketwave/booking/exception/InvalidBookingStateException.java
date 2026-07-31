package com.ticketwave.booking.exception;

import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class InvalidBookingStateException extends TicketwaveException {

    public InvalidBookingStateException(Long bookingId, BookingStatus currentStatus, BookingStatus attemptedStatus) {
        super(HttpStatus.CONFLICT, "INVALID_BOOKING_STATE",
                "Booking " + bookingId + " is " + currentStatus + " and cannot transition to " + attemptedStatus);
    }
}
