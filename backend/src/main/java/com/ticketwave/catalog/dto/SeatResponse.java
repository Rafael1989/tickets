package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.SeatStatus;

import java.math.BigDecimal;

public record SeatResponse(
        Long id,
        Long scheduleId,
        String seatNumber,
        String seatClass,
        SeatStatus status,
        BigDecimal priceModifier
) {
}
