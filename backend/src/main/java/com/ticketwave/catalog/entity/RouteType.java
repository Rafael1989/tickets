package com.ticketwave.catalog.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum RouteType implements CodedEnum {

    FLIGHT("flight"),
    BUS("bus"),
    TRAIN("train"),
    EVENT("event");

    private final String code;

    RouteType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
