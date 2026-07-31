package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.entity.ScheduleStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Denormalizes route + schedule + a real-time seat count into one search-result
 * shape, distinct from ScheduleResponse (plain CRUD view, no route/seat detail).
 */
public record ScheduleSearchResult(
        Long scheduleId,
        Long routeId,
        RouteType type,
        String origin,
        String destination,
        String venue,
        Instant departureTime,
        Instant arrivalTime,
        BigDecimal baseFare,
        String currency,
        ScheduleStatus status,
        long availableSeats
) {
}
