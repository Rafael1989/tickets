package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.SeatRequest;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.dto.SeatUpdateRequest;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.mapper.SeatMapper;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.SeatRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatManagementServiceImpl implements SeatManagementService {

    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final SeatMapper seatMapper;

    public SeatManagementServiceImpl(
            ScheduleRepository scheduleRepository,
            SeatRepository seatRepository,
            SeatMapper seatMapper
    ) {
        this.scheduleRepository = scheduleRepository;
        this.seatRepository = seatRepository;
        this.seatMapper = seatMapper;
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

        return seatMapper.toResponse(seatRepository.save(seat));
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public SeatResponse updateSeat(String operatorUsername, Long seatId, SeatUpdateRequest request) {
        Seat seat = seatRepository.findById(seatId)
                .filter(candidate -> candidate.getSchedule().getRoute().getOperator().getUsername().equals(operatorUsername))
                .orElseThrow(() -> new SeatNotFoundException(seatId));

        seat.setStatus(request.status());
        seat.setPriceModifier(request.priceModifier());

        return seatMapper.toResponse(seat);
    }
}
