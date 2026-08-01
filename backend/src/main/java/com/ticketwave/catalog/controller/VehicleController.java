package com.ticketwave.catalog.controller;

import com.ticketwave.catalog.dto.VehicleRequest;
import com.ticketwave.catalog.dto.VehicleResponse;
import com.ticketwave.catalog.service.VehicleService;
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
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicles", description = "Requires a bearer JWT with the OPERATOR role.")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    @Operation(summary = "Create a vehicle owned by the authenticated operator")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vehicle created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator")
    })
    public ResponseEntity<VehicleResponse> createVehicle(
            Authentication authentication,
            @Valid @RequestBody VehicleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleService.createVehicle(authentication.getName(), request));
    }

    @GetMapping("/mine")
    @Operation(summary = "List the authenticated operator's vehicles")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicles"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator")
    })
    public ResponseEntity<List<VehicleResponse>> listMyVehicles(Authentication authentication) {
        return ResponseEntity.ok(vehicleService.listMyVehicles(authentication.getName()));
    }
}
