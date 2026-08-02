package com.ticketwave.pricing.controller;

import com.ticketwave.pricing.dto.PromoCodeApplication;
import com.ticketwave.pricing.dto.PromoCodeRequest;
import com.ticketwave.pricing.dto.PromoCodeResponse;
import com.ticketwave.pricing.dto.PromoCodeStatusUpdateRequest;
import com.ticketwave.pricing.dto.PromoValidationRequest;
import com.ticketwave.pricing.dto.PromoValidationResponse;
import com.ticketwave.pricing.service.PricingService;
import com.ticketwave.pricing.service.PromoCodeService;
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
@RequestMapping("/api/promos")
@Tag(name = "Promotions", description = "POST /validate is public; every other endpoint requires the ADMIN role.")
public class PromoController {

    private final PricingService pricingService;
    private final PromoCodeService promoCodeService;

    public PromoController(PricingService pricingService, PromoCodeService promoCodeService) {
        this.pricingService = pricingService;
        this.promoCodeService = promoCodeService;
    }

    @PostMapping("/validate")
    @Operation(
            summary = "Preview a promo code's discount without redeeming it",
            description = "Read-only: does not reserve a redemption. subtotal is caller-supplied for display purposes only — booking creation always recalculates the authoritative subtotal from the seats actually held.",
            security = {}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code is valid; discount computed against the given subtotal"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "No such promo code"),
            @ApiResponse(responseCode = "409", description = "Code exists but isn't currently usable (inactive, outside its validity window, or fully redeemed)"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<PromoValidationResponse> validate(@Valid @RequestBody PromoValidationRequest request) {
        PromoCodeApplication application = pricingService.previewPromoCode(request.code(), request.subtotal());
        return ResponseEntity.ok(new PromoValidationResponse(
                application.promoCode().getCode(),
                application.discountAmount(),
                request.subtotal().subtract(application.discountAmount())
        ));
    }

    @PostMapping
    @Operation(summary = "Create a promo code")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Promo code created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "409", description = "Code already taken")
    })
    public ResponseEntity<PromoCodeResponse> createPromoCode(
            Authentication authentication,
            @Valid @RequestBody PromoCodeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(promoCodeService.createPromoCode(authentication.getName(), request));
    }

    @GetMapping
    @Operation(summary = "List every promo code")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Promo codes"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public ResponseEntity<List<PromoCodeResponse>> listPromoCodes() {
        return ResponseEntity.ok(promoCodeService.listPromoCodes());
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a promo code")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "No such promo code")
    })
    public ResponseEntity<PromoCodeResponse> updateStatus(
            Authentication authentication,
            @PathVariable("id") Long promoCodeId,
            @Valid @RequestBody PromoCodeStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(promoCodeService.updateStatus(authentication.getName(), promoCodeId, request.active()));
    }
}
