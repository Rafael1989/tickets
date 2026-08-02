package com.ticketwave.reporting.dto;

import com.ticketwave.catalog.model.RouteType;

import java.math.BigDecimal;

public record RouteReportItem(
        Long routeId,
        RouteType type,
        String origin,
        String destination,
        String venue,
        long confirmedBookings,
        BigDecimal revenue,
        long totalSeats,
        long bookedSeats,
        BigDecimal occupancyRate
) {
}
