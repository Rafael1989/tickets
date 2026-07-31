package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class RefundNotFoundException extends TicketwaveException {

    public RefundNotFoundException(Long refundId) {
        super(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "Refund " + refundId + " was not found");
    }
}
