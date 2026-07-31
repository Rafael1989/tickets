package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.SeatStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Distinct from SeatRequest (creation): a seat's schedule/seatNumber/class
 * are structural identity, not something an update call should be able to
 * move. Only status and fare are mutable after creation.
 */
public record SeatUpdateRequest(
        @NotNull SeatStatus status,
        @NotNull @DecimalMin("0.0") @Digits(integer = 3, fraction = 3) BigDecimal priceModifier
) {
}
