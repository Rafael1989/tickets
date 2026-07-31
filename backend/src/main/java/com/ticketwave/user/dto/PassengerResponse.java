package com.ticketwave.user.dto;

import java.time.LocalDate;

public record PassengerResponse(
        Long id,
        Long userId,
        String fullName,
        LocalDate dob,
        String idType,
        String idNumber
) {
}
