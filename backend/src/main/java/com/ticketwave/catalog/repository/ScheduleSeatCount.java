package com.ticketwave.catalog.repository;

/**
 * Projection for a batched per-schedule seat count (see
 * SeatRepository#countAvailableGroupedByScheduleId), letting search results
 * be annotated with availability in one grouped query instead of one COUNT
 * per schedule.
 */
public interface ScheduleSeatCount {

    Long getScheduleId();

    long getAvailableCount();
}
