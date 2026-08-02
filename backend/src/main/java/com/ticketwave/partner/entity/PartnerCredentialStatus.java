package com.ticketwave.partner.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum PartnerCredentialStatus implements CodedEnum {

    ACTIVE("active"),
    REVOKED("revoked");

    private final String code;

    PartnerCredentialStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
