package com.ticketwave.pricing.dto;

import jakarta.validation.constraints.NotNull;

public record PromoCodeStatusUpdateRequest(
        @NotNull Boolean active
) {
}
