package com.ticketwave.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Deliberately has no `role` field: self-registration always creates a
 * CUSTOMER account. Operator/support/admin accounts are provisioned through
 * an admin-only path, never chosen by the registering caller.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Email @Size(max = 255) String email
) {
}
