package com.ticketwave.catalog.controller;

import com.ticketwave.catalog.dto.ScheduleSearchResult;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.service.ScheduleSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@Tag(name = "Schedules", description = "Public guest browsing — no account needed. Rate-limited.")
public class ScheduleController {

    private final ScheduleSearchService scheduleSearchService;

    public ScheduleController(ScheduleSearchService scheduleSearchService) {
        this.scheduleSearchService = scheduleSearchService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get schedule details", security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schedule found"),
            @ApiResponse(responseCode = "404", description = "No such schedule"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<ScheduleSearchResult> getSchedule(@PathVariable("id") Long scheduleId) {
        return ResponseEntity.ok(scheduleSearchService.getScheduleDetails(scheduleId));
    }

    @GetMapping("/{id}/seats")
    @Operation(
            summary = "Get seat availability for a schedule",
            description = "Returns every seat on the schedule, including HELD/BOOKED ones, so a client can render a full seat map rather than just the bookable subset.",
            security = {}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Seat list"),
            @ApiResponse(responseCode = "404", description = "No such schedule"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<List<SeatResponse>> getSeats(@PathVariable("id") Long scheduleId) {
        return ResponseEntity.ok(scheduleSearchService.getSeatsForSchedule(scheduleId));
    }
}
