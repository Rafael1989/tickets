package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.SeatRequest;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.dto.SeatUpdateRequest;

public interface SeatManagementService {

    /**
     * Operator-only. Adds a seat to a schedule owned by the authenticated
     * operator.
     *
     * @throws com.ticketwave.catalog.exception.ScheduleNotFoundException if the schedule doesn't exist or isn't owned by this operator
     */
    SeatResponse addSeat(String operatorUsername, SeatRequest request);

    /**
     * Operator-only. Updates a seat's status/fare on a schedule owned by the
     * authenticated operator.
     *
     * @throws com.ticketwave.catalog.exception.SeatNotFoundException if the seat doesn't exist or isn't owned by this operator
     */
    SeatResponse updateSeat(String operatorUsername, Long seatId, SeatUpdateRequest request);
}
