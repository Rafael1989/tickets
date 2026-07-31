package com.ticketwave.catalog.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum SeatStatus implements CodedEnum {

    AVAILABLE("available"),
    HELD("held"),
    BOOKED("booked");

    private final String code;

    SeatStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
