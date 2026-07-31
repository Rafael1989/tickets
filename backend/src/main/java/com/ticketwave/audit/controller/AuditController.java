package com.ticketwave.audit.controller;

import com.ticketwave.audit.dto.AuditLogResponse;
import com.ticketwave.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Audit", description = "Requires a bearer JWT with the ADMIN role.")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/api/audit")
    @Operation(summary = "List audit log entries, most recent first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit log entries"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public ResponseEntity<List<AuditLogResponse>> listAudit() {
        return ResponseEntity.ok(auditService.listAll());
    }
}
