package com.ticketwave.ledger.controller;

import com.ticketwave.ledger.dto.ReconciliationReportResponse;
import com.ticketwave.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@Tag(name = "Ledger", description = "Requires a bearer JWT with the ADMIN role.")
public class ReconciliationController {

    private final LedgerService ledgerService;

    public ReconciliationController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/api/ledger/reconciliation")
    @Operation(
            summary = "Aggregate payments/refunds/adjustments recorded in a date range",
            description = "from is inclusive, to is exclusive. Backed by an append-only ledger entry per settled payment and processed refund — see LedgerService."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reconciliation totals for the range"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public ResponseEntity<ReconciliationReportResponse> getReconciliationReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(ledgerService.reconcile(from, to));
    }
}
