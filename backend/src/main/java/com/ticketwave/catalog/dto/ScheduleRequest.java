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
 * absent, matching the schema's column default. vehicleId/driverId are both
 * optional - assigning either is checked for a time-overlap against that
 * vehicle's/driver's other schedules (see ScheduleManagementServiceImpl);
 * omitting one leaves the schedule with no assignment (or clears an existing
 * one, on update).
 */
public record ScheduleRequest(
        @NotNull Long routeId,
        @NotNull Instant departureTime,
        @NotNull Instant arrivalTime,
        @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal baseFare,
        @NotNull @Size(min = 3, max = 3) String currency,
        ScheduleStatus status,
        Long vehicleId,
        Long driverId
) {
    public ScheduleRequest(Long routeId, Instant departureTime, Instant arrivalTime, BigDecimal baseFare,
                            String currency, ScheduleStatus status) {
        this(routeId, departureTime, arrivalTime, baseFare, currency, status, null, null);
    }
}
