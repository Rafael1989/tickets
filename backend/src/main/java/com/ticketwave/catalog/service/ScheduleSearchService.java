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
     *
     * @throws com.ticketwave.catalog.exception.ScheduleNotFoundException if no such schedule exists
     */
    List<SeatResponse> getSeatsForSchedule(Long scheduleId);
}
