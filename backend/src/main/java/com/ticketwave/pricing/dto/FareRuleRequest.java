package com.ticketwave.pricing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * surchargeRate follows the same convention as PricingProperties' rates: a
 * positive value is a surcharge, negative is a discount (e.g. 0.20 = +20%,
 * -0.10 = -10%), applied on top of the existing demand-based multiplier.
 */
public record FareRuleRequest(
        @NotNull Long routeId,
        @NotBlank @Size(max = 20) String seatClass,
        @NotNull Instant validFrom,
        @NotNull Instant validTo,
        @NotNull @DecimalMin("-1.0") @DecimalMax("5.0") @Digits(integer = 2, fraction = 4) BigDecimal surchargeRate
) {
}
