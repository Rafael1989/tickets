package com.ticketwave.catalog.repository;

/** Projection for a batched per-route seat inventory/occupancy count — see SeatRepository. */
public interface RouteSeatStats {

    Long getRouteId();

    long getTotalSeats();

    long getBookedSeats();
}
