package com.ticketwave.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for domain exceptions (e.g. SeatUnavailableException,
 * BookingNotFoundException) added in later phases. Each subclass supplies
 * the HTTP status and a stable machine-readable error code that
 * GlobalExceptionHandler maps into the API error body.
 */
public abstract class TicketwaveException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected TicketwaveException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
