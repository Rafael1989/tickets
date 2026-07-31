package com.ticketwave.catalog.dto;

import com.ticketwave.catalog.entity.RouteType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * operatorId is deliberately not a field here: a route is always created
 * under the authenticated operator, resolved server-side from the JWT
 * principal, the same way CreateBookingRequest and PassengerRequest handle
 * ownership.
 */
public record RouteRequest(
        @NotNull RouteType type,
        @Size(max = 150) String origin,
        @Size(max = 150) String destination,
        @Size(max = 150) String venue,
        @Positive Integer durationMinutes
) {
}
