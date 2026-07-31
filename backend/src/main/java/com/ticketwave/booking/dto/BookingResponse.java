package com.ticketwave.booking.dto;

import com.ticketwave.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(
        Long id,
        Long userId,
        Long scheduleId,
        String pnr,
        Instant bookingTime,
        BookingStatus status,
        BigDecimal totalAmount,
        String promoCode
) {
}
