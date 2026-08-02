package com.ticketwave.booking.repository;

import java.math.BigDecimal;

/** Projection for a batched per-route count/revenue of CONFIRMED bookings — see BookingRepository. */
public interface RouteBookingStats {

    Long getRouteId();

    long getBookingCount();

    BigDecimal getRevenue();
}
