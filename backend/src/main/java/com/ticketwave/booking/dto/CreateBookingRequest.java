package com.ticketwave.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The full atomic creation shape: a booking always comes into existence with
 * its seat/passenger selections already chosen, since seats are held as part
 * of creation (not added afterward) — distinct from the plain BookingRequest,
 * which has no place to carry seat selections. promoCode is optional.
 *
 * userId is deliberately not a field here: the booking is always created for
 * the authenticated caller, resolved server-side from the JWT principal, so
 * there's no way for a request body to create a booking under someone else's
 * identity.
 */
public record CreateBookingRequest(
        @NotNull Long scheduleId,
        @NotEmpty List<@Valid SeatSelection> seatSelections,
        @Size(max = 30) String promoCode
) {
}
