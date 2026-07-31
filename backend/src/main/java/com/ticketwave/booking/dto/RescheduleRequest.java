package com.ticketwave.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Only usable while the booking is still INITIATED (unpaid): changing seats
 * on a schedule you haven't paid for yet is a free operation, no billing
 * logic needed. A CONFIRMED (paid) booking still goes through the existing
 * refund-then-rebook flow to change plans, since reconciling a fare
 * difference against money already charged is a separate feature.
 */
public record RescheduleRequest(
        @NotNull Long scheduleId,
        @NotEmpty List<@Valid SeatSelection> seatSelections
) {
}
