package com.ticketwave.partner.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class DuplicatePartnerException extends TicketwaveException {

    public DuplicatePartnerException(String name) {
        super(HttpStatus.CONFLICT, "DUPLICATE_PARTNER", "Partner name '" + name + "' is already taken");
    }
}
