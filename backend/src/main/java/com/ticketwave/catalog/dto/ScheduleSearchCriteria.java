package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.model.RouteType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Every field is optional; an all-null criteria matches every non-cancelled
 * schedule. origin/destination apply to travel routes, venue to events.
 * minPrice/maxPrice filter on the schedule's base_fare (not the final
 * per-seat price, which also factors in class/demand modifiers â€” base_fare
 * is the only price dimension a schedule has before a seat is chosen).
 * seatClass matches schedules with at least one seat of that class, in any
 * status. sortBy defaults to DEPARTURE_TIME when null.
 */
public record ScheduleSearchCriteria(
        RouteType type,
        String origin,
        String destination,
        String venue,
        LocalDate departureDate,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String seatClass,
        ScheduleSortBy sortBy
) {
}
