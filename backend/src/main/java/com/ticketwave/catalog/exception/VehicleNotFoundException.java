package com.ticketwave.catalog.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class VehicleNotFoundException extends TicketwaveException {

    public VehicleNotFoundException(Long vehicleId) {
        super(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", "Vehicle " + vehicleId + " was not found");
    }
}
