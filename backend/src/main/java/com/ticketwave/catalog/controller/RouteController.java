package com.ticketwave.catalog.controller;

import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@Tag(name = "Routes", description = "Requires a bearer JWT with the OPERATOR role.")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
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
