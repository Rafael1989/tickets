package com.ticketwave.catalog.controller;

import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.dto.ScheduleResponse;
import com.ticketwave.catalog.service.RouteService;
import com.ticketwave.catalog.service.ScheduleManagementService;
import com.ticketwave.pricing.dto.FareRuleResponse;
import com.ticketwave.pricing.service.FareRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@Tag(name = "Routes", description = "Requires a bearer JWT with the OPERATOR role.")
public class RouteController {

    private final RouteService routeService;
    private final ScheduleManagementService scheduleManagementService;
    private final FareRuleService fareRuleService;

    public RouteController(
            RouteService routeService,
            ScheduleManagementService scheduleManagementService,
            FareRuleService fareRuleService
    ) {
        this.routeService = routeService;
        this.scheduleManagementService = scheduleManagementService;
        this.fareRuleService = fareRuleService;
    }

    @PostMapping
    @Operation(summary = "Create a route owned by the authenticated operator")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Route created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator")
    })
    public ResponseEntity<RouteResponse> createRoute(
            Authentication authentication,
            @Valid @RequestBody RouteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(routeService.createRoute(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a route owned by the authenticated operator")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Route updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator"),
            @ApiResponse(responseCode = "404", description = "No such route, or it belongs to a different operator")
    })
    public ResponseEntity<RouteResponse> updateRoute(
            Authentication authentication,
            @PathVariable("id") Long routeId,
            @Valid @RequestBody RouteRequest request
    ) {
        return ResponseEntity.ok(routeService.updateRoute(authentication.getName(), routeId, request));
    }

    @GetMapping("/{id}/schedules")
    @Operation(summary = "List schedules under a route owned by the authenticated operator")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schedules"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator"),
            @ApiResponse(responseCode = "404", description = "No such route, or it belongs to a different operator")
    })
    public ResponseEntity<List<ScheduleResponse>> listSchedulesForRoute(
            Authentication authentication,
            @PathVariable("id") Long routeId
    ) {
        return ResponseEntity.ok(scheduleManagementService.listSchedulesForRoute(authentication.getName(), routeId));
    }

    @GetMapping("/{id}/fare-rules")
    @Operation(summary = "List fare rules under a route owned by the authenticated operator")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fare rules"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator"),
            @ApiResponse(responseCode = "404", description = "No such route, or it belongs to a different operator")
    })
    public ResponseEntity<List<FareRuleResponse>> listFareRulesForRoute(
            Authentication authentication,
            @PathVariable("id") Long routeId
    ) {
        return ResponseEntity.ok(fareRuleService.listFareRulesForRoute(authentication.getName(), routeId));
    }

    @GetMapping("/mine")
    @Operation(summary = "List the authenticated operator's routes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Routes"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator")
    })
    public ResponseEntity<List<RouteResponse>> listMyRoutes(Authentication authentication) {
        return ResponseEntity.ok(routeService.listMyRoutes(authentication.getName()));
    }
}
