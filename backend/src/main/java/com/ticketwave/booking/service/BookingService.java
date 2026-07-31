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
     * INITIATED -> CONFIRMED. Confirms every held seat as BOOKED.
     *
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently INITIATED
     */
    BookingDetailResponse confirmBooking(Long bookingId);

    /**
     * INITIATED -> CANCELLED, releasing every held seat. Cancelling an
     * already-CONFIRMED (paid) booking involves refund policy and is handled
     * by the refund flow, not here.
     *
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently INITIATED
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
     * Changes an INITIATED (unpaid) booking's schedule/seats in place,
     * releasing the old holds and re-pricing against the new schedule. Not
     * available once a booking is CONFIRMED — see RescheduleRequest's javadoc.
     *
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently INITIATED
     * @throws com.ticketwave.catalog.exception.ScheduleNotFoundException if the new schedule doesn't exist
     * @throws com.ticketwave.user.exception.PassengerNotFoundException if a passenger doesn't exist or isn't owned by this booking's customer
     */
    BookingDetailResponse rescheduleBooking(Long bookingId, RescheduleRequest request);
}
