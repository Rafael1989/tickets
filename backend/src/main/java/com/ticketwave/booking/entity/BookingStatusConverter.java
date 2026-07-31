package com.ticketwave.booking.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BookingStatusConverter extends CodedEnumConverter<BookingStatus> {

    public BookingStatusConverter() {
        super(BookingStatus.class);
    }
}
