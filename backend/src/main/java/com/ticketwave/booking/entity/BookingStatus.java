package com.ticketwave.booking.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum BookingStatus implements CodedEnum {

    INITIATED("initiated"),
    CONFIRMED("confirmed"),
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
