package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.RouteType;

public record RouteResponse(
        Long id,
        Long operatorId,
        RouteType type,
        String origin,
        String destination,
        String venue,
        Integer durationMinutes
) {
}
