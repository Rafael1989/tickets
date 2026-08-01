package com.ticketwave.catalog.service;

import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.user.entity.User;

/**
 * Owns the seat hold state machine: AVAILABLE -> HELD (with an expiration) ->
 * BOOKED, or back to AVAILABLE on release/expiry. Booking creation/confirmation
 * (a later phase) orchestrates calls into this rather than mutating Seat
 * status itself.
 */
public interface SeatHoldService {

    /**
     * Holds the seat for the configured TTL. Succeeds if the seat is
     * AVAILABLE, HELD with an expired hold (reclaimed on-access), or already
     * HELD by this same user (idempotent re-affirm — refreshes the TTL
     * rather than erroring, so both a fresh seat-map selection and a later
     * booking-creation call over the same seat by its own holder both
     * succeed).
     *
     * @throws com.ticketwave.catalog.exception.SeatNotFoundException if no such seat exists
     * @throws com.ticketwave.catalog.exception.SeatUnavailableException if the seat is BOOKED, or HELD by someone else and not yet expired
     */
    Seat holdSeat(Long seatId, User heldBy);

    /**
     * Resolves username to a User and delegates to {@link #holdSeat(Long, User)}
     * — the entry point for the customer-facing pre-checkout hold endpoint,
     * which only has the authenticated username, not a loaded User.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if username doesn't resolve to a user
     */
    Seat holdSeatForUsername(Long seatId, String username);

    /**
     * Releases a HELD or BOOKED seat back to AVAILABLE (the latter for
     * cancelling an already-paid booking). A no-op if the seat is already
     * AVAILABLE. Unconditional — the caller (booking cancellation, refund)
     * is responsible for its own authorization check before calling this.
     */
    void releaseSeat(Long seatId);

    /**
     * Releases the given seat only if it's currently HELD by this username —
     * a silent no-op otherwise (whether it's AVAILABLE, BOOKED, or HELD by
     * someone else), so a caller can never distinguish "wasn't held" from
     * "held by someone else" and can never release a hold that isn't theirs.
     */
    void releaseOwnHold(Long seatId, String username);

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
