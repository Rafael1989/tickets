package com.ticketwave.catalog.dto;

public record DriverResponse(
        Long id,
        Long operatorId,
        String fullName,
        String licenseNumber
) {
}
