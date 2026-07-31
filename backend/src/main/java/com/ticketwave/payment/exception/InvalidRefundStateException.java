package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import com.ticketwave.payment.entity.RefundStatus;
import org.springframework.http.HttpStatus;

public class InvalidRefundStateException extends TicketwaveException {

    public InvalidRefundStateException(Long refundId, RefundStatus currentStatus) {
        super(HttpStatus.CONFLICT, "INVALID_REFUND_STATE",
                "Refund " + refundId + " is already " + currentStatus + " and cannot be processed again");
    }
}
