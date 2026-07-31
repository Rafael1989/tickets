package com.ticketwave.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Thresholds and rates for the demand-based portion of dynamic pricing.
 * Occupancy thresholds/discount-surcharge rates are expressed as fractions
 * (0.80 = 80%, 0.15 = +15%), not percentages.
 */
@ConfigurationProperties(prefix = "ticketwave.pricing")
public record PricingProperties(
        long lastMinuteThresholdHours,
        BigDecimal lastMinuteSurchargeRate,
        long earlyBirdThresholdDays,
        BigDecimal earlyBirdDiscountRate,
        BigDecimal highOccupancyThreshold,
        BigDecimal highOccupancySurchargeRate,
        BigDecimal lowOccupancyThreshold,
        BigDecimal lowOccupancyDiscountRate
) {
}
