package com.ticketwave.user.dto;

import java.time.Instant;

public record UserPreferencesResponse(
        Long userId,
        String preferredCurrency,
        String seatPreference,
        boolean notificationsEnabled,
        Instant updatedAt
) {
}
