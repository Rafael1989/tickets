package com.ticketwave.user.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class SelfRoleChangeException extends TicketwaveException {

    public SelfRoleChangeException(Long userId) {
        super(HttpStatus.CONFLICT, "SELF_ROLE_CHANGE_NOT_ALLOWED",
                "An admin cannot change their own role (user " + userId + ") — have another admin make this change");
    }
}
