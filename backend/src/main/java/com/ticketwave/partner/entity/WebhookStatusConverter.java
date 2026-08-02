package com.ticketwave.partner.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class WebhookStatusConverter extends CodedEnumConverter<WebhookStatus> {

    public WebhookStatusConverter() {
        super(WebhookStatus.class);
    }
}
