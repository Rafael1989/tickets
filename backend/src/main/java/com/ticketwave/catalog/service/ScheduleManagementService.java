package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.ScheduleRequest;
import com.ticketwave.catalog.dto.ScheduleResponse;

import java.util.List;

public interface ScheduleManagementService {

    /**
     * Operator-only. Creates a schedule under a route owned by the
     * authenticated operator.
     *
     * @throws com.ticketwave.catalog.exception.RouteNotFoundException if the route doesn't exist or isn't owned by this operator
     */
    ScheduleResponse createSchedule(String operatorUsername, ScheduleRequest request);

    /**
     * Operator-only. Updates a schedule owned by the authenticated operator
     * (via its route). Setting status to CANCELLED is how a schedule is
     * retired - see ScheduleSpecifications.isNotCancelled, already excluded
     * from customer-facing search.
     *
     * @throws com.ticketwave.catalog.exception.ScheduleNotFoundException if no such schedule exists, or it isn't owned by this operator
     */
    ScheduleResponse updateSchedule(String operatorUsername, Long scheduleId, ScheduleRequest request);

    /**
     * Operator-only. Lists every schedule under a route owned by the
     * authenticated operator.
     *
     * @throws com.ticketwave.catalog.exception.RouteNotFoundException if no such route exists, or it isn't owned by this operator
     */
    List<ScheduleResponse> listSchedulesForRoute(String operatorUsername, Long routeId);
}
