package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.ScheduleStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ScheduleResponse(
        Long id,
        Long routeId,
        Instant departureTime,
        Instant arrivalTime,
        BigDecimal baseFare,
        String currency,
        ScheduleStatus status
) {
}
