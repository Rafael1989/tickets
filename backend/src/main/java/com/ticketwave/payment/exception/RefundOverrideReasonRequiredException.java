package com.ticketwave.payment.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class RefundOverrideReasonRequiredException extends TicketwaveException {

    public RefundOverrideReasonRequiredException(Long refundId) {
        super(HttpStatus.BAD_REQUEST, "REFUND_OVERRIDE_REASON_REQUIRED",
                "Overriding refund " + refundId + "'s amount requires a reason explaining the waiver");
    }
}
