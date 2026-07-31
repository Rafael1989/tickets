package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.RouteType;

import java.time.LocalDate;

/**
 * Every field is optional; an all-null criteria matches every non-cancelled
 * schedule. origin/destination apply to travel routes, venue to events.
 */
public record ScheduleSearchCriteria(
        RouteType type,
        String origin,
        String destination,
        String venue,
        LocalDate departureDate
) {
}
