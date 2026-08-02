package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class RefundOverrideAmountExceedsPaymentException extends TicketwaveException {

    public RefundOverrideAmountExceedsPaymentException(Long refundId, BigDecimal overrideAmount, BigDecimal paymentAmount) {
        super(HttpStatus.BAD_REQUEST, "REFUND_OVERRIDE_EXCEEDS_PAYMENT",
                "Override amount " + overrideAmount + " for refund " + refundId
                        + " exceeds the underlying payment's amount of " + paymentAmount);
    }
}
