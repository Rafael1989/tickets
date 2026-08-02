package com.ticketwave.reporting.controller;

import com.ticketwave.reporting.dto.OperatorReportResponse;
import com.ticketwave.reporting.service.OperatorReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator/reports")
@Tag(name = "Operator Reports", description = "Requires a bearer JWT with the OPERATOR role.")
public class OperatorReportController {

    private final OperatorReportService operatorReportService;

    public OperatorReportController(OperatorReportService operatorReportService) {
        this.operatorReportService = operatorReportService;
    }

    @GetMapping
    @Operation(summary = "Confirmed bookings, revenue, and seat occupancy per route the caller can manage")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report computed (empty routes list if the caller owns none)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an operator")
    })
    public ResponseEntity<OperatorReportResponse> getReport(Authentication authentication) {
        return ResponseEntity.ok(operatorReportService.getReport(authentication.getName()));
    }
}
