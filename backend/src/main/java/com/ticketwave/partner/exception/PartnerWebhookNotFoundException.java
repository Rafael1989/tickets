package com.ticketwave.partner.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class PartnerWebhookNotFoundException extends TicketwaveException {

    public PartnerWebhookNotFoundException(Long webhookId) {
        super(HttpStatus.NOT_FOUND, "PARTNER_WEBHOOK_NOT_FOUND", "Partner webhook " + webhookId + " was not found");
    }
}
