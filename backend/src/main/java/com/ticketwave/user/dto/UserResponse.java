package com.ticketwave.user.dto;

import com.ticketwave.user.entity.UserRole;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        UserRole role,
        Instant createdAt
) {
}
