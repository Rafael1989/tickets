package com.ticketwave.booking.dto;

import java.math.BigDecimal;

public record BookingItemResponse(
        Long id,
        Long bookingId,
        Long seatId,
        Long passengerId,
        BigDecimal fare
) {
}
