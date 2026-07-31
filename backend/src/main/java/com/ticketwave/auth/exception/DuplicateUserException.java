package com.ticketwave.auth.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class DuplicateUserException extends TicketwaveException {

    public DuplicateUserException(String message) {
        super(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", message);
    }
}
