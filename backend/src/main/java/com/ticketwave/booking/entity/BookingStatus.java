package com.ticketwave.booking.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum BookingStatus implements CodedEnum {

    INITIATED("initiated"),
    PAYMENT_PROCESSING("payment_processing"),
    CONFIRMED("confirmed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String code;

    BookingStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
