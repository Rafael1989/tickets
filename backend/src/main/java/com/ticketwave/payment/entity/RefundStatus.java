package com.ticketwave.payment.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum RefundStatus implements CodedEnum {

    PENDING("pending"),
    PROCESSED("processed"),
    REJECTED("rejected");

    private final String code;

    RefundStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
