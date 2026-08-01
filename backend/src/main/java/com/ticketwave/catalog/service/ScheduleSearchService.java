package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.dto.ScheduleSearchResult;
import com.ticketwave.catalog.dto.SeatResponse;

import java.util.List;

public interface ScheduleSearchService {

    List<ScheduleSearchResult> search(ScheduleSearchCriteria criteria);

    long getAvailableSeatCount(Long scheduleId);

    /**
     * @throws com.ticketwave.catalog.exception.ScheduleNotFoundException if no such schedule exists
     */
    ScheduleSearchResult getScheduleDetails(Long scheduleId);

    /**
     * Every seat on the schedule (not just AVAILABLE ones), so a client can
     * render a full seat map with unavailable seats shown, not just omitted.
     * Each seat's estimatedFare mirrors exactly what booking creation would
     * charge (same PricingService calculation), and heldByMe is true only
     * when the given username currently holds that seat — false for every
     * other seat and for a null (guest) username.
     *
     * @throws com.ticketwave.catalog.exception.ScheduleNotFoundException if no such schedule exists
     */
    List<SeatResponse> getSeatsForSchedule(Long scheduleId, String username);
}
