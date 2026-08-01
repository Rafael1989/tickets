package com.ticketwave.catalog.controller;

import com.ticketwave.catalog.dto.DriverRequest;
import com.ticketwave.catalog.dto.DriverResponse;
import com.ticketwave.catalog.service.DriverService;
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
@RequestMapping("/api/drivers")
@Tag(name = "Drivers", description = "Requires a bearer JWT with the OPERATOR role.")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    @Operation(summary = "Create a driver owned by the authenticated operator")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Driver created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator")
    })
    public ResponseEntity<DriverResponse> createDriver(
            Authentication authentication,
            @Valid @RequestBody DriverRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driverService.createDriver(authentication.getName(), request));
    }

    @GetMapping("/mine")
    @Operation(summary = "List the authenticated operator's drivers")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Drivers"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator")
    })
    public ResponseEntity<List<DriverResponse>> listMyDrivers(Authentication authentication) {
        return ResponseEntity.ok(driverService.listMyDrivers(authentication.getName()));
    }
}
