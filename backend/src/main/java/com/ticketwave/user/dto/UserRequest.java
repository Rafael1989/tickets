package com.ticketwave.user.dto;

import com.ticketwave.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * password is the raw plaintext value supplied at the API boundary; the
 * service layer hashes it before it ever reaches the User entity.
 */
public record UserRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull UserRole role
) {
}
