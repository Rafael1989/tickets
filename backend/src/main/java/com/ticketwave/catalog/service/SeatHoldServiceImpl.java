package com.ticketwave.catalog.service;

import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.exception.SeatUnavailableException;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.config.InventoryProperties;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class SeatHoldServiceImpl implements SeatHoldService {

    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final InventoryProperties inventoryProperties;

    public SeatHoldServiceImpl(
            SeatRepository seatRepository,
            UserRepository userRepository,
            InventoryProperties inventoryProperties
    ) {
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.inventoryProperties = inventoryProperties;
    }

    @Override
    @Transactional
    public Seat holdSeat(Long seatId, User heldBy) {
        Seat seat = findForUpdate(seatId);

        boolean heldByCaller = seat.getStatus() == SeatStatus.HELD
                && seat.getHeldBy() != null
                && seat.getHeldBy().getId().equals(heldBy.getId());

        if (seat.getStatus() != SeatStatus.AVAILABLE && !isExpiredHold(seat) && !heldByCaller) {
            throw new SeatUnavailableException(seatId);
        }

        seat.setStatus(SeatStatus.HELD);
        seat.setHeldBy(heldBy);
        seat.setHeldUntil(Instant.now().plus(inventoryProperties.seatHoldTtlMinutes(), ChronoUnit.MINUTES));
        return seat;
    }

    @Override
    @Transactional
    public Seat holdSeatForUsername(Long seatId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        return holdSeat(seatId, user);
    }

    /**
     * Also releases a BOOKED seat (not just HELD), since cancelling an
     * already-CONFIRMED (paid) booking must give the seat back to inventory
     * too — the refund flow drives that path via BookingService.cancelBooking.
     */
    @Override
    @Transactional
    public void releaseSeat(Long seatId) {
        Seat seat = findForUpdate(seatId);

        if (seat.getStatus() == SeatStatus.HELD || seat.getStatus() == SeatStatus.BOOKED) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHeldUntil(null);
            seat.setHeldBy(null);
        }
    }

    @Override
    @Transactional
    public void releaseOwnHold(Long seatId, String username) {
        Seat seat = findForUpdate(seatId);

        boolean heldByCaller = seat.getStatus() == SeatStatus.HELD
                && seat.getHeldBy() != null
                && seat.getHeldBy().getUsername().equals(username);

        if (heldByCaller) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHeldUntil(null);
            seat.setHeldBy(null);
        }
    }

    @Override
    @Transactional
    public void confirmHold(Long seatId) {
        Seat seat = findForUpdate(seatId);

        if (seat.getStatus() != SeatStatus.HELD || isExpiredHold(seat)) {
            throw new SeatUnavailableException(seatId);
        }

        seat.setStatus(SeatStatus.BOOKED);
        seat.setHeldUntil(null);
        seat.setHeldBy(null);
    }

    @Override
    @Transactional
    public int releaseExpiredHolds() {
        return seatRepository.releaseExpiredHolds(Instant.now());
    }

    private Seat findForUpdate(Long seatId) {
        return seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new SeatNotFoundException(seatId));
    }

    private boolean isExpiredHold(Seat seat) {
        return seat.getStatus() == SeatStatus.HELD
                && seat.getHeldUntil() != null
                && seat.getHeldUntil().isBefore(Instant.now());
    }
}
