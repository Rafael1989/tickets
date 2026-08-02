package com.ticketwave.partner.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum PartnerStatus implements CodedEnum {

    PENDING("pending"),
    ACTIVE("active"),
    SUSPENDED("suspended");

    private final String code;

    PartnerStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
