package com.ticketwave.auth.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class IncorrectPasswordException extends TicketwaveException {

    public IncorrectPasswordException() {
        super(HttpStatus.UNAUTHORIZED, "INCORRECT_PASSWORD", "Current password is incorrect");
    }
}
