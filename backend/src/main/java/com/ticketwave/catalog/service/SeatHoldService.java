package com.ticketwave.catalog.service;

import com.ticketwave.catalog.entity.Seat;

/**
 * Owns the seat hold state machine: AVAILABLE -> HELD (with an expiration) ->
 * BOOKED, or back to AVAILABLE on release/expiry. Booking creation/confirmation
 * (a later phase) orchestrates calls into this rather than mutating Seat
 * status itself.
 */
public interface SeatHoldService {

    /**
     * Holds the seat for the configured TTL. Succeeds if the seat is
     * AVAILABLE, or HELD with an expired hold (reclaimed on-access).
     *
     * @throws com.ticketwave.catalog.exception.SeatNotFoundException if no such seat exists
     * @throws com.ticketwave.catalog.exception.SeatUnavailableException if the seat is BOOKED, or HELD and not yet expired
     */
    Seat holdSeat(Long seatId);

    /**
     * Releases a HELD or BOOKED seat back to AVAILABLE (the latter for
     * cancelling an already-paid booking). A no-op if the seat is already
     * AVAILABLE.
     */
    void releaseSeat(Long seatId);

    /**
     * Confirms a HELD seat as BOOKED.
     *
     * @throws com.ticketwave.catalog.exception.SeatUnavailableException if the seat isn't HELD, or its hold has already expired
     */
    void confirmHold(Long seatId);

    /**
     * Bulk-reclaims every seat whose hold has expired. Invoked by the
     * background sweeper; returns the number of seats released.
     */
    int releaseExpiredHolds();
}
