package com.ticketwave.catalog.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum SeatStatus implements CodedEnum {

    AVAILABLE("available"),
    HELD("held"),
    BOOKED("booked"),
    /** Operator-blocked, e.g. for maintenance. Never touched by the customer hold/release flow. */
    BLOCKED("blocked"),
    /** Operator-reserved, e.g. driver/crew or VIP seating. Never touched by the customer hold/release flow. */
    RESERVED_OPERATOR("reserved_operator");

    private final String code;

    SeatStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
