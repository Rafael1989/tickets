package com.ticketwave.payment.controller;

import com.ticketwave.payment.dto.RefundDecisionRequest;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/refunds")
@Tag(name = "Refunds", description = "Requires a bearer JWT with the SUPPORT or ADMIN role.")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PutMapping("/{id}/process")
    @Operation(summary = "Settle a PENDING refund as PROCESSED or REJECTED")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refund settled"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not support or admin"),
            @ApiResponse(responseCode = "404", description = "No such refund"),
            @ApiResponse(responseCode = "409", description = "Refund isn't currently PENDING")
    })
    public ResponseEntity<RefundResponse> processRefund(
            Authentication authentication,
            @PathVariable("id") Long refundId,
            @Valid @RequestBody RefundDecisionRequest request
    ) {
        return ResponseEntity.ok(refundService.processRefund(refundId, authentication.getName(), request.decision()));
    }
}
