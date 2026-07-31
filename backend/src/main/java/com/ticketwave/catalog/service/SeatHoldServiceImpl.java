package com.ticketwave.catalog.service;

import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.exception.SeatUnavailableException;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.config.InventoryProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class SeatHoldServiceImpl implements SeatHoldService {

    private final SeatRepository seatRepository;
    private final InventoryProperties inventoryProperties;

    public SeatHoldServiceImpl(SeatRepository seatRepository, InventoryProperties inventoryProperties) {
        this.seatRepository = seatRepository;
        this.inventoryProperties = inventoryProperties;
    }

    @Override
    @Transactional
    public Seat holdSeat(Long seatId) {
        Seat seat = findForUpdate(seatId);

        if (seat.getStatus() != SeatStatus.AVAILABLE && !isExpiredHold(seat)) {
            throw new SeatUnavailableException(seatId);
        }

        seat.setStatus(SeatStatus.HELD);
        seat.setHeldUntil(Instant.now().plus(inventoryProperties.seatHoldTtlMinutes(), ChronoUnit.MINUTES));
        return seat;
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
