package com.ticketwave.reporting.service;

import com.ticketwave.reporting.dto.OperatorReportResponse;

public interface OperatorReportService {

    /**
     * Confirmed-bookings/revenue/occupancy per route visible to the caller —
     * their own routes, or their partner's if they belong to one (same
     * "mine" broadens to "my partner's" rule as RouteService.listMyRoutes).
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if operatorUsername doesn't resolve to a user
     */
    OperatorReportResponse getReport(String operatorUsername);
}
