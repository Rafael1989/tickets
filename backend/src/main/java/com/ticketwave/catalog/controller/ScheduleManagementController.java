package com.ticketwave.catalog.controller;

import com.ticketwave.catalog.dto.ScheduleRequest;
import com.ticketwave.catalog.dto.ScheduleResponse;
import com.ticketwave.catalog.dto.SeatRequest;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.dto.SeatUpdateRequest;
import com.ticketwave.catalog.service.ScheduleManagementService;
import com.ticketwave.catalog.service.SeatManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Inventory management", description = "Requires a bearer JWT with the OPERATOR role.")
public class ScheduleManagementController {

    private final ScheduleManagementService scheduleManagementService;
    private final SeatManagementService seatManagementService;

    public ScheduleManagementController(
            ScheduleManagementService scheduleManagementService,
            SeatManagementService seatManagementService
    ) {
        this.scheduleManagementService = scheduleManagementService;
        this.seatManagementService = seatManagementService;
    }

    @PostMapping("/api/schedules")
    @Operation(summary = "Create a schedule under a route owned by the authenticated operator")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Schedule created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator"),
            @ApiResponse(responseCode = "404", description = "No such route, or it belongs to a different operator")
    })
    public ResponseEntity<ScheduleResponse> createSchedule(
            Authentication authentication,
            @Valid @RequestBody ScheduleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduleManagementService.createSchedule(authentication.getName(), request));
    }

    @PostMapping("/api/seats")
    @Operation(summary = "Add a seat to a schedule owned by the authenticated operator")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Seat created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator"),
            @ApiResponse(responseCode = "404", description = "No such schedule, or it belongs to a different operator")
    })
    public ResponseEntity<SeatResponse> addSeat(
            Authentication authentication,
            @Valid @RequestBody SeatRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seatManagementService.addSeat(authentication.getName(), request));
    }

    @PutMapping("/api/seats/{id}")
    @Operation(summary = "Update a seat's status/fare on a schedule owned by the authenticated operator")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Seat updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator"),
            @ApiResponse(responseCode = "404", description = "No such seat, or it belongs to a different operator")
    })
    public ResponseEntity<SeatResponse> updateSeat(
            Authentication authentication,
            @PathVariable("id") Long seatId,
            @Valid @RequestBody SeatUpdateRequest request
    ) {
        return ResponseEntity.ok(seatManagementService.updateSeat(authentication.getName(), seatId, request));
    }
}
