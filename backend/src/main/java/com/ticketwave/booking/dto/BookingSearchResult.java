package com.ticketwave.booking.dto;

import com.ticketwave.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A row in a booking list — enough to identify and triage a booking
 * (customer, route, status, amount) without a second round trip to
 * GET /api/bookings/{id} for every match. Shared by the support omni-search
 * (GET /api/bookings/search) and a customer's own booking list
 * (GET /api/bookings/me).
 */
public record BookingSearchResult(
        Long bookingId,
        String pnr,
        BookingStatus status,
        BigDecimal totalAmount,
        Instant bookingTime,
        String customerUsername,
        String customerEmail,
        String origin,
        String destination,
        Instant departureTime
) {
}
