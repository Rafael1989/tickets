package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import com.ticketwave.payment.entity.PaymentStatus;
import org.springframework.http.HttpStatus;

public class InvalidPaymentStateException extends TicketwaveException {

    public InvalidPaymentStateException(Long paymentId, PaymentStatus actual, PaymentStatus required) {
        super(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE",
                "Payment " + paymentId + " is " + actual + ", not " + required);
    }
}
