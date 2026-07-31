package com.ticketwave.catalog.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ScheduleStatusConverter extends CodedEnumConverter<ScheduleStatus> {

    public ScheduleStatusConverter() {
        super(ScheduleStatus.class);
    }
}
