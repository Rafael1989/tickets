package com.ticketwave.partner.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class PartnerCredentialNotFoundException extends TicketwaveException {

    public PartnerCredentialNotFoundException(Long credentialId) {
        super(HttpStatus.NOT_FOUND, "PARTNER_CREDENTIAL_NOT_FOUND", "Partner API credential " + credentialId + " was not found");
    }
}
