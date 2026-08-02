package com.ticketwave.partner.controller;

import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.partner.service.PartnerResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/partner")
@Tag(name = "Partner API", description = "Requires a bearer JWT issued by POST /api/oauth/token (PARTNER_API role) — for a partner's own systems, not human logins.")
public class PartnerResourceController {

    private final PartnerResourceService partnerResourceService;

    public PartnerResourceController(PartnerResourceService partnerResourceService) {
        this.partnerResourceService = partnerResourceService;
    }

    @GetMapping("/routes")
    @Operation(summary = "List every route owned by the calling credential's partner")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Routes"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid bearer token, or the credential/partner is no longer active")
    })
    public ResponseEntity<List<RouteResponse>> listRoutes(Authentication authentication) {
        return ResponseEntity.ok(partnerResourceService.listRoutes(authentication.getName()));
    }
}
