package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.RouteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
        @NotNull RouteType type,
        @NotBlank @Size(max = 50) String identifier,
        @NotNull @Positive Integer capacity,
        @Size(max = 100) String model
) {
}
