package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.ScheduleStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * status is optional on create; the service defaults it to SCHEDULED when
 * absent, matching the schema's column default.
 */
public record ScheduleRequest(
        @NotNull Long routeId,
        @NotNull Instant departureTime,
        @NotNull Instant arrivalTime,
        @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal baseFare,
        @NotNull @Size(min = 3, max = 3) String currency,
        ScheduleStatus status
) {
}
