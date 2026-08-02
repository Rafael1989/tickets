package com.ticketwave.booking.service;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.BookingSearchResult;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.RescheduleRequest;

import java.util.List;

public interface BookingService {

    /**
     * Creates a booking in INITIATED status for the given (authenticated)
     * username, holding every selected seat as part of the same transaction
     * — if any seat can't be held, nothing is created.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if username doesn't resolve to a user
     */
    BookingDetailResponse createBooking(String username, CreateBookingRequest request);

    /**
     * INITIATED, FAILED, or already PAYMENT_PROCESSING -> PAYMENT_PROCESSING.
     * Marks a payment attempt as in flight; FAILED is accepted alongside
     * INITIATED so a declined payment can be retried, and re-affirming an
     * already-PAYMENT_PROCESSING booking is a no-op (a concurrent request
     * racing the same in-flight attempt, not a fresh one). Only a booking
     * that's already been settled (CONFIRMED or CANCELLED) is rejected.
     *
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking is currently CONFIRMED or CANCELLED
     */
    BookingDetailResponse markPaymentProcessing(Long bookingId);

    /**
     * PAYMENT_PROCESSING -> CONFIRMED. Confirms every held seat as BOOKED.
     *
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently PAYMENT_PROCESSING
     */
    BookingDetailResponse confirmBooking(Long bookingId);

    /**
     * PAYMENT_PROCESSING -> FAILED. Does not release the held seats, so the
     * customer can retry the payment against the same held seats.
     *
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently PAYMENT_PROCESSING
     */
    BookingDetailResponse failBooking(Long bookingId);

    /**
     * INITIATED, CONFIRMED, or FAILED -> CANCELLED, releasing every held
     * seat. Not available while a payment attempt is in flight
     * (PAYMENT_PROCESSING). Cancelling an already-CONFIRMED (paid) booking
     * involves refund policy and is handled by the refund flow, not here.
     *
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking is already CANCELLED or currently PAYMENT_PROCESSING
     */
    BookingDetailResponse cancelBooking(Long bookingId);

    /**
     * Retrieves a booking's full detail (booking + items). Restricted to the
     * booking's own customer, or support/admin.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     */
    BookingDetailResponse getBooking(Long bookingId);

    /**
     * Compatibility shim for callers expecting an explicit "confirm" step.
     * This API only ever confirms a booking as a result of a successful
     * payment (see {@code markPaymentProcessing}/{@code confirmBooking}
     * driven by the payment flow) — a booking can never be confirmed
     * without having paid. This method does not drive that transition; it
     * only succeeds as a no-op when the booking is already CONFIRMED, and
     * rejects otherwise, so it can never be used to bypass payment.
     * Restricted to the booking's own customer, or support/admin.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently CONFIRMED
     */
    BookingDetailResponse requireConfirmed(Long bookingId);

    /**
     * Support/admin only — the customer-facing lookup path is by booking id
     * (getBooking), reached via navigation within the app; PNR lookup is for
     * a support agent looking up a booking a customer has quoted to them.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such PNR exists
     */
    BookingDetailResponse getBookingByPnr(String pnr);

    /**
     * The authenticated customer's own bookings, newest first — every status
     * (INITIATED/CONFIRMED/CANCELLED/etc.), not filtered down to just the
     * active ones, so a customer can find a past or cancelled trip too.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if username doesn't resolve to a user
     */
    List<BookingSearchResult> listMyBookings(String username);

    /**
     * Support/admin omni-search: an exact PNR match, or a substring match
     * (case-insensitive) against the customer's email or a passenger's full
     * name. Returns at most the newest 25 matches, newest booking first — a
     * broad query (e.g. a common last name) is expected to be narrowed by
     * the agent, not to return an unbounded result set. A blank query
     * returns no results rather than every booking.
     */
    List<BookingSearchResult> searchBookings(String query);

    /**
     * Public "find my booking" lookup for a guest with no account/session:
     * PNR plus the email on the booking as a second factor, so a bare PNR
     * guess can't retrieve someone else's itinerary. Fails the same generic
     * way whether the PNR doesn't exist or the email doesn't match it,
     * mirroring auth.exception.InvalidCredentialsException's reasoning.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such PNR exists, or email doesn't match
     */
    BookingDetailResponse lookupByPnrAndEmail(String pnr, String email);

    /**
     * The mechanical seat/schedule swap for an INITIATED or CONFIRMED
     * booking: releases the old holds, holds the new seats, and re-prices
     * against the new schedule. For a CONFIRMED booking, callers should go
     * through {@code com.ticketwave.payment.service.RescheduleService}
     * instead of calling this directly — it applies the departure-proximity
     * eligibility check and the fare-difference charge/credit that this
     * method knows nothing about.
     *
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently INITIATED or CONFIRMED
     * @throws com.ticketwave.catalog.exception.ScheduleNotFoundException if the new schedule doesn't exist
     * @throws com.ticketwave.user.exception.PassengerNotFoundException if a passenger doesn't exist or isn't owned by this booking's customer
     */
    BookingDetailResponse rescheduleBooking(Long bookingId, RescheduleRequest request);
}
