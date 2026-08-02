package com.ticketwave.partner.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum WebhookStatus implements CodedEnum {

    ACTIVE("active"),
    DISABLED("disabled");

    private final String code;

    WebhookStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
