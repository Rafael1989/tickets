package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.ScheduleRequest;
import com.ticketwave.catalog.dto.ScheduleResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.exception.RouteNotFoundException;
import com.ticketwave.catalog.mapper.ScheduleMapper;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.catalog.repository.ScheduleRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleManagementServiceImpl implements ScheduleManagementService {

    private final RouteRepository routeRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleMapper scheduleMapper;

    public ScheduleManagementServiceImpl(
            RouteRepository routeRepository,
            ScheduleRepository scheduleRepository,
            ScheduleMapper scheduleMapper
    ) {
        this.routeRepository = routeRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleMapper = scheduleMapper;
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public ScheduleResponse createSchedule(String operatorUsername, ScheduleRequest request) {
        Route route = routeRepository.findById(request.routeId())
                .filter(candidate -> candidate.getOperator().getUsername().equals(operatorUsername))
                .orElseThrow(() -> new RouteNotFoundException(request.routeId()));

        Schedule schedule = scheduleMapper.toEntity(request, route);
        if (schedule.getStatus() == null) {
            schedule.setStatus(ScheduleStatus.SCHEDULED);
        }

        return scheduleMapper.toResponse(scheduleRepository.save(schedule));
    }
}
