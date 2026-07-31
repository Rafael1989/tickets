package com.ticketwave.payment.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentStatusConverter extends CodedEnumConverter<PaymentStatus> {

    public PaymentStatusConverter() {
        super(PaymentStatus.class);
    }
}
