package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.SeatStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * status is optional on create; the service defaults it to AVAILABLE when
 * absent, matching the schema's column default.
 */
public record SeatRequest(
        @NotNull Long scheduleId,
        @NotBlank @Size(max = 10) String seatNumber,
        @NotBlank @Size(max = 20) String seatClass,
        SeatStatus status,
        @NotNull @DecimalMin("0.0") @Digits(integer = 3, fraction = 3) BigDecimal priceModifier
) {
}
