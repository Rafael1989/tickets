package com.ticketwave.ledger.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LedgerEntryTypeConverter extends CodedEnumConverter<LedgerEntryType> {

    public LedgerEntryTypeConverter() {
        super(LedgerEntryType.class);
    }
}
