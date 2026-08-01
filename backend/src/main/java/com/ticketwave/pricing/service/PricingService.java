package com.ticketwave.pricing.service;

import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.pricing.dto.PromoCodeApplication;

import java.math.BigDecimal;

public interface PricingService {

    /**
     * baseFare * seat's own price modifier * a real-time demand multiplier
     * (how close to departure, how full the schedule already is).
     */
    BigDecimal calculateSeatFare(Schedule schedule, Seat seat);

    /**
     * Validates the code (exists, active, within its validity window, under
     * its redemption limit), reserves one redemption, and returns the
     * discount to subtract from subtotal. Always consumes a redemption —
     * only booking creation should call this. For a non-consuming preview,
     * see {@link #previewPromoCode(String, BigDecimal)}.
     *
     * @throws com.ticketwave.pricing.exception.PromoCodeNotFoundException if no such code exists
     * @throws com.ticketwave.pricing.exception.PromoCodeNotApplicableException if the code exists but can't currently be used
     */
    PromoCodeApplication applyPromoCode(String code, BigDecimal subtotal);

    /**
     * Same validation as {@link #applyPromoCode(String, BigDecimal)} — exists,
     * active, within its validity window, under its redemption limit — but
     * never reserves a redemption. For a promo-code input to show "valid,
     * saves you $X" before the caller has committed to a booking.
     *
     * @throws com.ticketwave.pricing.exception.PromoCodeNotFoundException if no such code exists
     * @throws com.ticketwave.pricing.exception.PromoCodeNotApplicableException if the code exists but can't currently be used
     */
    PromoCodeApplication previewPromoCode(String code, BigDecimal subtotal);
}
