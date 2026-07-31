package com.ticketwave.booking.dto;

import jakarta.validation.constraints.NotNull;

public record SeatSelection(
        @NotNull Long seatId,
        @NotNull Long passengerId
) {
}
