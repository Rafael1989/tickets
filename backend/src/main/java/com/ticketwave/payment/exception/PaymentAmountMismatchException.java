package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class PaymentAmountMismatchException extends TicketwaveException {

    public PaymentAmountMismatchException(Long bookingId, BigDecimal expected, BigDecimal actual) {
        super(HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH",
                "Payment amount " + actual + " does not match booking " + bookingId + "'s total of " + expected);
    }
}
