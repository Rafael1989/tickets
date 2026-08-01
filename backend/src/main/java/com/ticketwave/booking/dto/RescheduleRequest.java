package com.ticketwave.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * For an INITIATED (unpaid) booking, changing seats is a free operation and
 * the payment fields are ignored. For a CONFIRMED (paid) booking, the new
 * seats' fare is compared against the current total; paymentMethod/
 * paymentReference/cardNumber are only required when that difference is a
 * net increase (an upgrade), so the difference can be collected the same way
 * checkout collects an initial payment. See RescheduleService for the full
 * eligibility/fare-difference orchestration - this record itself is just the
 * request shape.
 */
public record RescheduleRequest(
        @NotNull Long scheduleId,
        @NotEmpty List<@Valid SeatSelection> seatSelections,
        @Size(max = 30) String paymentMethod,
        @Size(max = 100) String paymentReference,
        @Pattern(regexp = "[0-9 ]{12,24}") String cardNumber
) {
    public RescheduleRequest(Long scheduleId, List<SeatSelection> seatSelections) {
        this(scheduleId, seatSelections, null, null, null);
    }
}
