package com.ticketwave.partner.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PartnerStatusConverter extends CodedEnumConverter<PartnerStatus> {

    public PartnerStatusConverter() {
        super(PartnerStatus.class);
    }
}
