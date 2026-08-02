package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.model.RouteType;

public record VehicleResponse(
        Long id,
        Long operatorId,
        RouteType type,
        String identifier,
        Integer capacity,
        String model
) {
}
