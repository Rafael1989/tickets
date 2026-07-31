package com.ticketwave.payment.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RefundStatusConverter extends CodedEnumConverter<RefundStatus> {

    public RefundStatusConverter() {
        super(RefundStatus.class);
    }
}
