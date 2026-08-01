package com.ticketwave.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserPreferencesRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code, e.g. USD") String preferredCurrency,
        @Size(max = 20) String seatPreference,
        @NotNull Boolean notificationsEnabled
) {
}
