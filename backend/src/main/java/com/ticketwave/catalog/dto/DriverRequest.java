package com.ticketwave.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DriverRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 50) String licenseNumber
) {
}
