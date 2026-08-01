package com.ticketwave.booking.dto;

import java.math.BigDecimal;

/**
 * A non-mutating preview of what rescheduling to the given schedule/seats
 * would cost, so a customer can see the fare-difference outcome before
 * committing. For an INITIATED booking, eligible is always true and
 * paymentRequired is always false (unpaid bookings reschedule for free,
 * regardless of the fare difference). For a CONFIRMED booking, eligible
 * reflects the same departure-proximity window as a cancellation; when
 * eligible, paymentRequired is true exactly when fareDifference is positive.
 * A negative fareDifference on a CONFIRMED booking means a credit will be
 * issued, not charged.
 */
public record RescheduleQuoteResponse(
        Long bookingId,
        BigDecimal currentTotal,
        BigDecimal newTotal,
        BigDecimal fareDifference,
        boolean eligible,
        boolean paymentRequired
) {
}
