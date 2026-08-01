package com.ticketwave.pricing.controller;

import com.ticketwave.pricing.dto.PromoCodeApplication;
import com.ticketwave.pricing.dto.PromoValidationRequest;
import com.ticketwave.pricing.dto.PromoValidationResponse;
import com.ticketwave.pricing.service.PricingService;
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
@RequestMapping("/api/promos")
@Tag(name = "Promotions", description = "Public — no account needed. Rate-limited.")
public class PromoController {

    private final PricingService pricingService;

    public PromoController(PricingService pricingService) {
        this.pricingService = pricingService;
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
}
