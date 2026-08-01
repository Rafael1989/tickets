package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class FareDifferencePaymentRequiredException extends TicketwaveException {

    public FareDifferencePaymentRequiredException(Long bookingId, BigDecimal amountDue) {
        super(HttpStatus.CONFLICT, "FARE_DIFFERENCE_PAYMENT_REQUIRED",
                "Rescheduling booking " + bookingId + " requires collecting a fare difference of " + amountDue
                        + " - paymentMethod, paymentReference (and cardNumber, for card) must be supplied");
    }
}
