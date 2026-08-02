package com.ticketwave.catalog.controller;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.dto.ScheduleSearchResult;
import com.ticketwave.catalog.dto.ScheduleSortBy;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.service.ScheduleSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@Tag(name = "Search", description = "Public guest browsing — no account needed. Rate-limited.")
public class SearchController {

    private final ScheduleSearchService scheduleSearchService;

    public SearchController(ScheduleSearchService scheduleSearchService) {
        this.scheduleSearchService = scheduleSearchService;
    }

    @GetMapping("/api/search")
    @Operation(
            summary = "Search schedules by type/origin/destination/venue/date/price/seat class",
            description = "Every filter is optional; an all-empty request matches every non-cancelled, not-yet-departed schedule. origin/destination/venue match case-insensitively on any substring. minPrice/maxPrice filter on the schedule's base fare. seatClass matches schedules with at least one seat of that class. sortBy defaults to soonest-departure-first. Each result includes a real-time available-seat count.",
            security = {}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching schedules, sorted per sortBy (soonest departure first by default)"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<List<ScheduleSearchResult>> search(
            @Parameter(description = "flight, bus, train, or event") @RequestParam(required = false) RouteType type,
            @Parameter(description = "Travel routes only, substring match, e.g. \"NY\"") @RequestParam(required = false) String origin,
            @Parameter(description = "Travel routes only, substring match") @RequestParam(required = false) String destination,
            @Parameter(description = "Events only, substring match, e.g. \"Arena\"") @RequestParam(required = false) String venue,
            @Parameter(description = "UTC calendar day, e.g. 2026-08-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @Parameter(description = "Minimum base fare") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum base fare") @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "e.g. \"economy\", \"business\"") @RequestParam(required = false) String seatClass,
            @Parameter(description = "DEPARTURE_TIME (default), PRICE_ASC, or PRICE_DESC") @RequestParam(required = false) ScheduleSortBy sortBy
    ) {
        ScheduleSearchCriteria criteria = new ScheduleSearchCriteria(
                type, origin, destination, venue, departureDate, minPrice, maxPrice, seatClass, sortBy);
        return ResponseEntity.ok(scheduleSearchService.search(criteria));
    }
}
