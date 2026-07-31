package com.ticketwave.pricing.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum DiscountType implements CodedEnum {

    PERCENTAGE("percentage"),
    FIXED_AMOUNT("fixed_amount");

    private final String code;

    DiscountType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
