package com.ticketwave.user.controller;

import com.ticketwave.user.dto.PassengerRequest;
import com.ticketwave.user.dto.PassengerResponse;
import com.ticketwave.user.service.PassengerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/passengers")
@Tag(name = "Passengers", description = "Requires a bearer JWT (see Authentication). Not rate-limited.")
public class PassengerController {

    private final PassengerService passengerService;

    public PassengerController(PassengerService passengerService) {
        this.passengerService = passengerService;
    }

    @PostMapping
    @Operation(summary = "Save a passenger profile for the authenticated caller")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Passenger created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    })
    public ResponseEntity<PassengerResponse> createPassenger(
            Authentication authentication,
            @Valid @RequestBody PassengerRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(passengerService.createPassenger(authentication.getName(), request));
    }

    @GetMapping("/me")
    @Operation(summary = "List the authenticated caller's saved passenger profiles")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Passenger profiles"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    })
    public ResponseEntity<List<PassengerResponse>> listMyPassengers(Authentication authentication) {
        return ResponseEntity.ok(passengerService.listMyPassengers(authentication.getName()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a saved passenger profile owned by the authenticated caller")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Passenger updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "404", description = "No such passenger, or it belongs to someone else")
    })
    public ResponseEntity<PassengerResponse> updatePassenger(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody PassengerRequest request
    ) {
        return ResponseEntity.ok(passengerService.updatePassenger(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved passenger profile owned by the authenticated caller")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Passenger deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "404", description = "No such passenger, or it belongs to someone else")
    })
    public ResponseEntity<Void> deletePassenger(Authentication authentication, @PathVariable("id") Long id) {
        passengerService.deletePassenger(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
