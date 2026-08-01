package com.ticketwave.catalog.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.dto.SeatRequest;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.dto.SeatUpdateRequest;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.exception.SeatUnavailableException;
import com.ticketwave.catalog.mapper.SeatMapper;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.SeatRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SeatManagementServiceImpl implements SeatManagementService {

    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final SeatMapper seatMapper;
    private final AuditService auditService;

    public SeatManagementServiceImpl(
            ScheduleRepository scheduleRepository,
            SeatRepository seatRepository,
            SeatMapper seatMapper,
            AuditService auditService
    ) {
        this.scheduleRepository = scheduleRepository;
        this.seatRepository = seatRepository;
        this.seatMapper = seatMapper;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public SeatResponse addSeat(String operatorUsername, SeatRequest request) {
        Schedule schedule = scheduleRepository.findById(request.scheduleId())
                .filter(candidate -> candidate.getRoute().getOperator().getUsername().equals(operatorUsername))
                .orElseThrow(() -> new ScheduleNotFoundException(request.scheduleId()));

        Seat seat = seatMapper.toEntity(request, schedule);
        if (seat.getStatus() == null) {
            seat.setStatus(SeatStatus.AVAILABLE);
        }

        Seat saved = seatRepository.save(seat);
        auditService.record(operatorUsername, "SEAT_ADDED", "SEAT", saved.getId(),
                "scheduleId=" + schedule.getId() + " seatNumber=" + saved.getSeatNumber());
        return seatMapper.toResponse(saved);
    }

    /**
     * Row-locks the seat (findByIdForUpdate) so this can never interleave
     * with SeatHoldServiceImpl's own pessimistic-locked hold/release/confirm
     * on the same row - without that lock, an operator's update and a
     * customer's concurrent checkout hold could race and silently overwrite
     * each other. On top of the lock, a BOOKED seat or a still-actively-HELD
     * seat is rejected outright: an operator taking a seat out of sale must
     * never silently clobber a real booking or a customer's in-progress
     * checkout - AVAILABLE, an already operator-owned status (BLOCKED/
     * RESERVED_OPERATOR), or an expired HELD hold are the only states this
     * can transition out of.
     */
    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public SeatResponse updateSeat(String operatorUsername, Long seatId, SeatUpdateRequest request) {
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .filter(candidate -> candidate.getSchedule().getRoute().getOperator().getUsername().equals(operatorUsername))
                .orElseThrow(() -> new SeatNotFoundException(seatId));

        if (seat.getStatus() == SeatStatus.BOOKED) {
            throw new SeatUnavailableException(seatId, "is booked and can't be modified here");
        }
        if (seat.getStatus() == SeatStatus.HELD && !isExpiredHold(seat)) {
            throw new SeatUnavailableException(seatId, "is currently held by a customer mid-checkout");
        }

        SeatStatus previousStatus = seat.getStatus();
        seat.setStatus(request.status());
        seat.setPriceModifier(request.priceModifier());
        // Whatever state it's leaving, this is an operator-driven transition,
        // never a customer hold - so any stale hold metadata is cleared.
        seat.setHeldBy(null);
        seat.setHeldUntil(null);

        auditService.record(operatorUsername, "SEAT_UPDATED", "SEAT", seat.getId(),
                "status=" + previousStatus + "->" + seat.getStatus() + " priceModifier=" + seat.getPriceModifier());
        return seatMapper.toResponse(seat);
    }

    private boolean isExpiredHold(Seat seat) {
        return seat.getHeldUntil() != null && seat.getHeldUntil().isBefore(Instant.now());
    }
}
