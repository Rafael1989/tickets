package com.ticketwave.catalog.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class DriverNotFoundException extends TicketwaveException {

    public DriverNotFoundException(Long driverId) {
        super(HttpStatus.NOT_FOUND, "DRIVER_NOT_FOUND", "Driver " + driverId + " was not found");
    }
}
