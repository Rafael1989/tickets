package com.ticketwave.catalog.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SeatStatusConverter extends CodedEnumConverter<SeatStatus> {

    public SeatStatusConverter() {
        super(SeatStatus.class);
    }
}
