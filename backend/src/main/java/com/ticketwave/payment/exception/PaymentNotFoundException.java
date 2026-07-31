package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class PaymentNotFoundException extends TicketwaveException {

    public PaymentNotFoundException(Long bookingId) {
        super(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "No successful payment was found for booking " + bookingId);
    }
}
