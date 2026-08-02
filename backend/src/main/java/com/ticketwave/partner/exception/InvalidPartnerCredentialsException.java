package com.ticketwave.partner.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

/**
 * Always carries the same generic message whether the clientId didn't
 * exist, the secret was wrong, the credential was revoked, or the owning
 * partner isn't ACTIVE — mirroring auth.exception.InvalidCredentialsException's
 * reasoning: never let a caller distinguish those cases from the outside.
 */
public class InvalidPartnerCredentialsException extends TicketwaveException {

    public InvalidPartnerCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_PARTNER_CREDENTIALS", "Invalid client credentials");
    }
}
