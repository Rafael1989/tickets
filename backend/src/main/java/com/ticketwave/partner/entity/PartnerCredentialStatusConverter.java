package com.ticketwave.partner.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PartnerCredentialStatusConverter extends CodedEnumConverter<PartnerCredentialStatus> {

    public PartnerCredentialStatusConverter() {
        super(PartnerCredentialStatus.class);
    }
}
