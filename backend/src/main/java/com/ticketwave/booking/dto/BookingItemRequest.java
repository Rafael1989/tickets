package com.ticketwave.booking.dto;

import jakarta.validation.constraints.NotNull;

/**
 * fare is computed server-side by the pricing engine and never accepted
 * from the client.
 */
public record BookingItemRequest(
        @NotNull Long bookingId,
        @NotNull Long seatId,
        @NotNull Long passengerId
) {
}
