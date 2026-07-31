package com.ticketwave.booking.dto;

import jakarta.validation.constraints.NotNull;

/**
 * pnr, status, bookingTime, and totalAmount are all server-computed
 * (PNR generation, pricing engine) and never accepted from the client.
 */
public record BookingRequest(
        @NotNull Long userId,
        @NotNull Long scheduleId
) {
}
