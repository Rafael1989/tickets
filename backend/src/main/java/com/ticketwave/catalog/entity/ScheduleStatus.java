package com.ticketwave.catalog.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum ScheduleStatus implements CodedEnum {

    SCHEDULED("scheduled"),
    DELAYED("delayed"),
    CANCELLED("cancelled"),
    COMPLETED("completed");

    private final String code;

    ScheduleStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
