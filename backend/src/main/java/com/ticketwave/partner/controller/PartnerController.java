package com.ticketwave.partner.controller;

import com.ticketwave.partner.dto.PartnerCredentialIssuedResponse;
import com.ticketwave.partner.dto.PartnerCredentialResponse;
import com.ticketwave.partner.dto.PartnerRequest;
import com.ticketwave.partner.dto.PartnerResponse;
import com.ticketwave.partner.dto.PartnerStatusUpdateRequest;
import com.ticketwave.partner.dto.PartnerWebhookIssuedResponse;
import com.ticketwave.partner.dto.PartnerWebhookRequest;
import com.ticketwave.partner.dto.PartnerWebhookResponse;
import com.ticketwave.partner.dto.WebhookStatusUpdateRequest;
import com.ticketwave.partner.service.PartnerApiCredentialService;
import com.ticketwave.partner.service.PartnerService;
import com.ticketwave.partner.service.PartnerWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/partners")
@Tag(name = "Partners", description = "Requires a bearer JWT with the ADMIN role.")
public class PartnerController {

    private final PartnerService partnerService;
    private final PartnerApiCredentialService credentialService;
    private final PartnerWebhookService webhookService;

    public PartnerController(
            PartnerService partnerService,
            PartnerApiCredentialService credentialService,
            PartnerWebhookService webhookService
    ) {
        this.partnerService = partnerService;
        this.credentialService = credentialService;
        this.webhookService = webhookService;
    }

    @PostMapping
    @Operation(summary = "Onboard a new partner", description = "Created in PENDING status; see PUT /{id}/status to activate it.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Partner created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "409", description = "Partner name already taken")
    })
    public ResponseEntity<PartnerResponse> createPartner(
            Authentication authentication,
            @Valid @RequestBody PartnerRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(partnerService.createPartner(authentication.getName(), request));
    }

    @GetMapping
    @Operation(summary = "List every partner")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partners"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public ResponseEntity<List<PartnerResponse>> listPartners() {
        return ResponseEntity.ok(partnerService.listPartners());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a partner by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partner found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "No such partner")
    })
    public ResponseEntity<PartnerResponse> getPartner(@PathVariable("id") Long partnerId) {
        return ResponseEntity.ok(partnerService.getPartner(partnerId));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Move a partner between PENDING/ACTIVE/SUSPENDED")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "No such partner")
    })
    public ResponseEntity<PartnerResponse> updateStatus(
            Authentication authentication,
            @PathVariable("id") Long partnerId,
            @Valid @RequestBody PartnerStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(partnerService.updateStatus(authentication.getName(), partnerId, request.status()));
    }

    @PostMapping("/{id}/credentials")
    @Operation(
            summary = "Issue a new OAuth2 client-credentials pair for a partner",
            description = "clientSecret is returned only in this response — capture it now, it can never be retrieved again."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Credential issued"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "No such partner")
    })
    public ResponseEntity<PartnerCredentialIssuedResponse> issueCredential(
            Authentication authentication,
            @PathVariable("id") Long partnerId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(credentialService.issueCredential(authentication.getName(), partnerId));
    }

    @GetMapping("/{id}/credentials")
    @Operation(summary = "List a partner's OAuth2 client credentials", description = "Never includes a secret.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credentials"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public ResponseEntity<List<PartnerCredentialResponse>> listCredentials(@PathVariable("id") Long partnerId) {
        return ResponseEntity.ok(credentialService.listCredentials(partnerId));
    }

    @PutMapping("/credentials/{credentialId}/revoke")
    @Operation(
            summary = "Revoke a partner API credential",
            description = "Stops new token issuance immediately. Already-issued tokens still expire on their own short TTL."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Credential revoked"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "No such credential")
    })
    public ResponseEntity<Void> revokeCredential(
            Authentication authentication,
            @PathVariable("credentialId") Long credentialId
    ) {
        credentialService.revokeCredential(authentication.getName(), credentialId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/webhooks")
    @Operation(
            summary = "Register a webhook target for a partner",
            description = "The signing secret is returned only in this response — capture it now, it can never be retrieved again."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Webhook registered"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "No such partner")
    })
    public ResponseEntity<PartnerWebhookIssuedResponse> registerWebhook(
            Authentication authentication,
            @PathVariable("id") Long partnerId,
            @Valid @RequestBody PartnerWebhookRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(webhookService.registerWebhook(authentication.getName(), partnerId, request));
    }

    @GetMapping("/{id}/webhooks")
    @Operation(summary = "List a partner's webhooks", description = "Never includes a signing secret.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhooks"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public ResponseEntity<List<PartnerWebhookResponse>> listWebhooks(@PathVariable("id") Long partnerId) {
        return ResponseEntity.ok(webhookService.listWebhooks(partnerId));
    }

    @PutMapping("/webhooks/{webhookId}/status")
    @Operation(summary = "Enable or disable a partner webhook")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "No such webhook")
    })
    public ResponseEntity<PartnerWebhookResponse> updateWebhookStatus(
            Authentication authentication,
            @PathVariable("webhookId") Long webhookId,
            @Valid @RequestBody WebhookStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(webhookService.updateStatus(authentication.getName(), webhookId, request.status()));
    }
}
