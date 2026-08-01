package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.RouteType;

public record VehicleResponse(
        Long id,
        Long operatorId,
        RouteType type,
        String identifier,
        Integer capacity,
        String model
) {
}
