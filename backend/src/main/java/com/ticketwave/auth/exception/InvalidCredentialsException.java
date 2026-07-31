package com.ticketwave.auth.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

/**
 * Always carries the same generic message regardless of whether the
 * username didn't exist or the password was wrong, so the API never lets a
 * caller distinguish the two (avoids username enumeration).
 */
public class InvalidCredentialsException extends TicketwaveException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }
}
