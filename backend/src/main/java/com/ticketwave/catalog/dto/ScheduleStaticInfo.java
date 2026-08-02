package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.model.RouteType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The route + schedule fields of ScheduleSearchResult that are safe to
 * cache — everything except availableSeats, which changes on every seat
 * hold/release/booking and is never cached (see ScheduleCatalogCache).
 * Deliberately a plain record, not the Schedule/Route entities themselves:
 * caching an entity risks a LazyInitializationException on a cache hit
 * served outside the transaction that originally loaded it.
 */
public record ScheduleStaticInfo(
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
        ScheduleStatus status
) {
}
