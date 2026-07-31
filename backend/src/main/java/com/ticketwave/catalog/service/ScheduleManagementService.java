package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.ScheduleRequest;
import com.ticketwave.catalog.dto.ScheduleResponse;

public interface ScheduleManagementService {

    /**
     * Operator-only. Creates a schedule under a route owned by the
     * authenticated operator.
     *
     * @throws com.ticketwave.catalog.exception.RouteNotFoundException if the route doesn't exist or isn't owned by this operator
     */
    ScheduleResponse createSchedule(String operatorUsername, ScheduleRequest request);
}
