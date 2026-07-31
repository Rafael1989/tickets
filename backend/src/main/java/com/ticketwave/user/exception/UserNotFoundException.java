package com.ticketwave.user.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends TicketwaveException {

    public UserNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User " + userId + " was not found");
    }

    public UserNotFoundException(String username) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User " + username + " was not found");
    }
}
