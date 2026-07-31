package com.ticketwave.user.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum UserRole implements CodedEnum {

    CUSTOMER("customer"),
    OPERATOR("operator"),
    SUPPORT("support"),
    ADMIN("admin");

    private final String code;

    UserRole(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
