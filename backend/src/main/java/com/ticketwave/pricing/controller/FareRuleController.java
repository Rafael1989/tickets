package com.ticketwave.pricing.controller;

import com.ticketwave.pricing.dto.FareRuleRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fare-rules")
@Tag(name = "Fare rules", description = "Requires a bearer JWT with the OPERATOR role.")
public class FareRuleController {

    private final FareRuleService fareRuleService;

    public FareRuleController(FareRuleService fareRuleService) {
        this.fareRuleService = fareRuleService;
    }

    @PostMapping
    @Operation(summary = "Create a single fare rule under a route owned by the authenticated operator")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Fare rule created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator"),
            @ApiResponse(responseCode = "404", description = "No such route, or it belongs to a different operator")
    })
    public ResponseEntity<FareRuleResponse> createFareRule(
            Authentication authentication,
            @Valid @RequestBody FareRuleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fareRuleService.createFareRule(authentication.getName(), request));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk-load fare rules, e.g. from a parsed CSV/Excel upload",
            description = "All-or-nothing: every row's route ownership is validated before any row is persisted, so one bad row can't leave a partial import."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Fare rules created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator"),
            @ApiResponse(responseCode = "404", description = "A row's route doesn't exist, or belongs to a different operator")
    })
    public ResponseEntity<List<FareRuleResponse>> bulkCreateFareRules(
            Authentication authentication,
            @Valid @RequestBody List<FareRuleRequest> requests
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fareRuleService.bulkCreateFareRules(authentication.getName(), requests));
    }
}
