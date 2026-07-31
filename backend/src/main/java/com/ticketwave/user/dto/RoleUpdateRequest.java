package com.ticketwave.user.dto;

import com.ticketwave.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
        @NotNull UserRole role
) {
}
