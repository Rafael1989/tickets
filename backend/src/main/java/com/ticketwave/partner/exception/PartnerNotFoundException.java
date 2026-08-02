package com.ticketwave.partner.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class PartnerNotFoundException extends TicketwaveException {

    public PartnerNotFoundException(Long partnerId) {
        super(HttpStatus.NOT_FOUND, "PARTNER_NOT_FOUND", "Partner " + partnerId + " was not found");
    }
}
