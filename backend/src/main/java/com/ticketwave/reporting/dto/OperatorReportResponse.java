package com.ticketwave.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

public record OperatorReportResponse(
        List<RouteReportItem> routes,
        long totalConfirmedBookings,
        BigDecimal totalRevenue
) {
}
