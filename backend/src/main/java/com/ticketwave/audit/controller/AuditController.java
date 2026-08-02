package com.ticketwave.audit.controller;

import com.ticketwave.audit.dto.AuditLogResponse;
import com.ticketwave.audit.dto.AuditLogSearchCriteria;
import com.ticketwave.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@Tag(name = "Audit", description = "Requires a bearer JWT with the ADMIN role.")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/api/audit")
    @Operation(
            summary = "Search audit log entries, most recent first",
            description = "Every filter is optional; an all-empty request matches every entry. actor matches a substring of the acting username; action/entityType match exactly."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching audit log entries"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public ResponseEntity<List<AuditLogResponse>> listAudit(
            @Parameter(description = "Substring match on the acting username") @RequestParam(required = false) String actor,
            @Parameter(description = "Exact match, e.g. USER_ROLE_CHANGED") @RequestParam(required = false) String action,
            @Parameter(description = "Exact match, e.g. USER or REFUND") @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(actor, action, entityType, from, to);
        return ResponseEntity.ok(auditService.search(criteria, PageRequest.of(page, size)));
    }
}
