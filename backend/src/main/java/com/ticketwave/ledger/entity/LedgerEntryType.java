package com.ticketwave.ledger.entity;

import com.ticketwave.common.persistence.CodedEnum;

public enum LedgerEntryType implements CodedEnum {

    PAYMENT("payment"),
    REFUND("refund"),
    ADJUSTMENT("adjustment");

    private final String code;

    LedgerEntryType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
