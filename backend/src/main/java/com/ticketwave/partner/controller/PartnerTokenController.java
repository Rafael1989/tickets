package com.ticketwave.partner.controller;

import com.ticketwave.partner.dto.PartnerTokenRequest;
import com.ticketwave.partner.dto.PartnerTokenResponse;
import com.ticketwave.partner.service.PartnerApiCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth")
@Tag(name = "Partner OAuth2", description = "Public — no bearer token needed. Rate-limited per client_id.")
public class PartnerTokenController {

    private final PartnerApiCredentialService credentialService;

    public PartnerTokenController(PartnerApiCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/token")
    @Operation(
            summary = "Exchange partner client credentials for a short-lived PARTNER_API access token",
            description = "OAuth2 client-credentials grant, simplified to JSON. The returned Bearer token authorizes partner-API resource endpoints.",
            security = {}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Invalid client_id/client_secret, revoked credential, or the partner isn't ACTIVE"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<PartnerTokenResponse> issueToken(@Valid @RequestBody PartnerTokenRequest request) {
        return ResponseEntity.ok(credentialService.issueToken(request.clientId(), request.clientSecret()));
    }
}
