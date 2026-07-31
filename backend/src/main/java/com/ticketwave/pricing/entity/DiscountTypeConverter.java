package com.ticketwave.pricing.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DiscountTypeConverter extends CodedEnumConverter<DiscountType> {

    public DiscountTypeConverter() {
        super(DiscountType.class);
    }
}
