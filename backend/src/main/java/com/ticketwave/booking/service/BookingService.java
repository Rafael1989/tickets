package com.ticketwave.booking.service;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.RescheduleRequest;

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
     * Support/admin only — the customer-facing lookup path is by booking id
     * (getBooking), reached via navigation within the app; PNR lookup is for
     * a support agent looking up a booking a customer has quoted to them.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such PNR exists
     */
    BookingDetailResponse getBookingByPnr(String pnr);

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
