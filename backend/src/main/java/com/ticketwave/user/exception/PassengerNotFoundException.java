package com.ticketwave.user.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class PassengerNotFoundException extends TicketwaveException {

    public PassengerNotFoundException(Long passengerId) {
        super(HttpStatus.NOT_FOUND, "PASSENGER_NOT_FOUND", "Passenger " + passengerId + " was not found");
    }
}
