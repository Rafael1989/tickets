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
     * discount to subtract from subtotal. There is no separate "preview"
     * method — this always consumes a redemption, since booking creation is
     * the only caller right now.
     *
     * @throws com.ticketwave.pricing.exception.PromoCodeNotFoundException if no such code exists
     * @throws com.ticketwave.pricing.exception.PromoCodeNotApplicableException if the code exists but can't currently be used
     */
    PromoCodeApplication applyPromoCode(String code, BigDecimal subtotal);
}
