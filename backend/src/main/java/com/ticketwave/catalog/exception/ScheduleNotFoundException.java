package com.ticketwave.catalog.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class ScheduleNotFoundException extends TicketwaveException {

    public ScheduleNotFoundException(Long scheduleId) {
        super(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule " + scheduleId + " was not found");
    }
}
