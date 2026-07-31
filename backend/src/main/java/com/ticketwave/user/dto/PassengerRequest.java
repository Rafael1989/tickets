package com.ticketwave.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * userId is deliberately not a field here: a passenger is always created
 * under the authenticated caller, resolved server-side from the JWT
 * principal, the same way CreateBookingRequest handles ownership.
 */
public record PassengerRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotNull @Past LocalDate dob,
        @NotBlank @Size(max = 30) String idType,
        @NotBlank @Size(max = 50) String idNumber
) {
}
