package com.ticketwave.user.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class PartnerAssignmentNotAllowedException extends TicketwaveException {

    public PartnerAssignmentNotAllowedException() {
        super(HttpStatus.BAD_REQUEST, "PARTNER_ASSIGNMENT_NOT_ALLOWED",
                "partnerId may only be set for an OPERATOR account");
    }
}
