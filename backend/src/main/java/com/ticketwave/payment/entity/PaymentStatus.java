package com.ticketwave.payment.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum PaymentStatus implements CodedEnum {

    PENDING("pending"),
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
