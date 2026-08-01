package com.ticketwave.user.controller;

import com.ticketwave.user.dto.UserPreferencesRequest;
import com.ticketwave.user.dto.UserPreferencesResponse;
import com.ticketwave.user.service.UserPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/preferences")
@Tag(name = "Preferences", description = "Requires a bearer JWT. Always operates on the authenticated caller's own preferences.")
public class PreferencesController {

    private final UserPreferencesService preferencesService;

    public PreferencesController(UserPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping
    @Operation(summary = "Get the authenticated caller's preferences, creating a default row if none exists yet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    })
    public ResponseEntity<UserPreferencesResponse> getPreferences(Authentication authentication) {
        return ResponseEntity.ok(preferencesService.getOrCreateDefault(authentication.getName()));
    }

    @PutMapping
    @Operation(summary = "Replace the authenticated caller's preferences")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    })
    public ResponseEntity<UserPreferencesResponse> updatePreferences(
            Authentication authentication,
            @Valid @RequestBody UserPreferencesRequest request
    ) {
        return ResponseEntity.ok(preferencesService.update(authentication.getName(), request));
    }
}
