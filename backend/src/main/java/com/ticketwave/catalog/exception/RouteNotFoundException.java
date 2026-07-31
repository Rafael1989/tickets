package com.ticketwave.catalog.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class RouteNotFoundException extends TicketwaveException {

    public RouteNotFoundException(Long routeId) {
        super(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "Route " + routeId + " was not found");
    }
}
