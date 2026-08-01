package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class FareDifferenceDeclinedException extends TicketwaveException {

    public FareDifferenceDeclinedException(Long bookingId, String declineReason) {
        super(HttpStatus.CONFLICT, "FARE_DIFFERENCE_DECLINED",
                "Fare difference payment for booking " + bookingId + " was declined: " + declineReason);
    }
}
