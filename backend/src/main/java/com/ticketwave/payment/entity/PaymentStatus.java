package com.ticketwave.payment.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum PaymentStatus implements CodedEnum {

    PENDING("pending"),
    /** Awaiting the simulated 3D Secure challenge — see PaymentService.confirmThreeDs. */
    PENDING_3DS("pending_3ds"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    REFUNDED("refunded");

    private final String code;

    PaymentStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
