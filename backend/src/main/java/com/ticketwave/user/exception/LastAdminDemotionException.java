package com.ticketwave.user.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class LastAdminDemotionException extends TicketwaveException {

    public LastAdminDemotionException(Long userId) {
        super(HttpStatus.CONFLICT, "LAST_ADMIN_DEMOTION_NOT_ALLOWED",
                "User " + userId + " is the last remaining ADMIN account and cannot be demoted — promote another admin first");
    }
}
