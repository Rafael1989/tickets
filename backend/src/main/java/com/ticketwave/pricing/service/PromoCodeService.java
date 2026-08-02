package com.ticketwave.pricing.service;

import com.ticketwave.pricing.dto.PromoCodeRequest;
import com.ticketwave.pricing.dto.PromoCodeResponse;

import java.util.List;

/**
 * Admin CRUD for promo codes — distinct from PricingService, which only
 * ever reads/redeems a promo code as a side effect of checkout, never
 * creates or lists them. Promo codes are platform-global (the entity has no
 * operator/route ownership), so this is ADMIN-only rather than OPERATOR,
 * consistent with other platform-wide configuration.
 */
public interface PromoCodeService {

    /**
     * @throws com.ticketwave.pricing.exception.DuplicatePromoCodeException if the code is already taken
     */
    PromoCodeResponse createPromoCode(String actorUsername, PromoCodeRequest request);

    List<PromoCodeResponse> listPromoCodes();

    /**
     * @throws com.ticketwave.pricing.exception.PromoCodeNotFoundException if no such promo code exists
     */
    PromoCodeResponse updateStatus(String actorUsername, Long promoCodeId, boolean active);
}
