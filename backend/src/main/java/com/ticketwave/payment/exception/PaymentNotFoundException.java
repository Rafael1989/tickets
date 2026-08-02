package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class PaymentNotFoundException extends TicketwaveException {

    public PaymentNotFoundException(Long bookingId) {
        this(HttpStatus.NOT_FOUND, "No successful payment was found for booking " + bookingId);
    }

    private PaymentNotFoundException(HttpStatus status, String message) {
        super(status, "PAYMENT_NOT_FOUND", message);
    }

    /** Distinct from the booking-keyed constructor above: used when a payment id itself doesn't resolve. */
    public static PaymentNotFoundException byId(Long paymentId) {
        return new PaymentNotFoundException(HttpStatus.NOT_FOUND, "Payment " + paymentId + " was not found");
    }
}
