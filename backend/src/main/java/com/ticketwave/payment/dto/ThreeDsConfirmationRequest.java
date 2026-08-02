package com.ticketwave.payment.dto;

import jakarta.validation.constraints.NotBlank;

/** code is the simulated one-time authentication code — matches CardDeclineSimulator's known test values, never a real 3DS provider. */
public record ThreeDsConfirmationRequest(
        @NotBlank String code
) {
}
