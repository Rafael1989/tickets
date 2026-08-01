package com.ticketwave.catalog.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class ScheduleConflictException extends TicketwaveException {

    public ScheduleConflictException(String resourceType, Long resourceId, Long conflictingScheduleId) {
        super(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT",
                resourceType + " " + resourceId + " is already assigned to an overlapping schedule (#"
                        + conflictingScheduleId + ")");
    }
}
