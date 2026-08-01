package com.ticketwave.payment.service;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.RescheduleQuoteResponse;
import com.ticketwave.booking.dto.RescheduleRequest;

import java.util.List;

/**
 * Orchestrates rescheduling on top of BookingServiceImpl's mechanical seat/
 * schedule swap: for an INITIATED (unpaid) booking it's a pure passthrough
 * (free, no eligibility window); for a CONFIRMED (paid) booking it also
 * enforces the same departure-proximity policy as a cancellation, then
 * settles the fare difference - collecting a top-up payment for an upgrade,
 * or issuing a RESCHEDULE_CREDIT refund for a downgrade.
 */
public interface RescheduleService {

    /**
     * Read-only: computes the fare difference and (for a CONFIRMED booking)
     * the eligibility/payment-required outcome, without changing anything.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't INITIATED or CONFIRMED
     * @throws com.ticketwave.catalog.exception.ScheduleNotFoundException if the target schedule doesn't exist
     * @throws com.ticketwave.catalog.exception.SeatNotFoundException if a selected seat doesn't exist
     */
    RescheduleQuoteResponse previewReschedule(Long bookingId, Long scheduleId, List<Long> seatIds);

    /**
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't INITIATED or CONFIRMED
     * @throws com.ticketwave.payment.exception.CancellationNotAllowedException if a CONFIRMED booking's departure is too imminent to reschedule
     * @throws com.ticketwave.payment.exception.PaymentNotFoundException if a CONFIRMED booking has no successful payment on record
     * @throws com.ticketwave.payment.exception.FareDifferencePaymentRequiredException if upgrading and no payment details were supplied
     * @throws com.ticketwave.payment.exception.FareDifferenceDeclinedException if upgrading and the simulated charge was declined
     */
    BookingDetailResponse reschedule(Long bookingId, RescheduleRequest request);
}
