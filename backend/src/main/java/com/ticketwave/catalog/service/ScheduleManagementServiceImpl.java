package com.ticketwave.catalog.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.dto.ScheduleRequest;
import com.ticketwave.catalog.dto.ScheduleResponse;
import com.ticketwave.catalog.entity.Driver;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.entity.Vehicle;
import com.ticketwave.catalog.exception.DriverNotFoundException;
import com.ticketwave.catalog.exception.RouteNotFoundException;
import com.ticketwave.catalog.exception.ScheduleConflictException;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.exception.VehicleNotFoundException;
import com.ticketwave.catalog.mapper.ScheduleMapper;
import com.ticketwave.catalog.repository.DriverRepository;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.VehicleRepository;
import com.ticketwave.catalog.security.TenantScope;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ScheduleManagementServiceImpl implements ScheduleManagementService {

    /** No real schedule can ever have this id - used as "exclude nothing" when checking a brand-new schedule for conflicts. */
    private static final Long NO_SCHEDULE_TO_EXCLUDE = 0L;

    private final RouteRepository routeRepository;
    private final ScheduleRepository scheduleRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final ScheduleMapper scheduleMapper;
    private final AuditService auditService;
    private final TenantScope tenantScope;

    public ScheduleManagementServiceImpl(
            RouteRepository routeRepository,
            ScheduleRepository scheduleRepository,
            VehicleRepository vehicleRepository,
            DriverRepository driverRepository,
            UserRepository userRepository,
            ScheduleMapper scheduleMapper,
            AuditService auditService,
            TenantScope tenantScope
    ) {
        this.routeRepository = routeRepository;
        this.scheduleRepository = scheduleRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
        this.scheduleMapper = scheduleMapper;
        this.auditService = auditService;
        this.tenantScope = tenantScope;
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public ScheduleResponse createSchedule(String operatorUsername, ScheduleRequest request) {
        User caller = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));
        Route route = routeRepository.findById(request.routeId())
                .filter(candidate -> tenantScope.isSameTenant(candidate.getOperator(), caller))
                .orElseThrow(() -> new RouteNotFoundException(request.routeId()));

        Vehicle vehicle = resolveVehicle(caller, request.vehicleId());
        Driver driver = resolveDriver(caller, request.driverId());
        requireNoVehicleConflict(vehicle, NO_SCHEDULE_TO_EXCLUDE, request.departureTime(), request.arrivalTime());
        requireNoDriverConflict(driver, NO_SCHEDULE_TO_EXCLUDE, request.departureTime(), request.arrivalTime());

        Schedule schedule = scheduleMapper.toEntity(request, route, vehicle, driver);
        if (schedule.getStatus() == null) {
            schedule.setStatus(ScheduleStatus.SCHEDULED);
        }

        Schedule saved = scheduleRepository.save(schedule);
        auditService.record(operatorUsername, "SCHEDULE_CREATED", "SCHEDULE", saved.getId(),
                "routeId=" + route.getId() + " departureTime=" + saved.getDepartureTime());
        return scheduleMapper.toResponse(saved);
    }

    /**
     * The route a schedule belongs to is structural identity, not something
     * this call can move - request.routeId() is ignored (ScheduleRequest is
     * reused as-is for symmetry with createSchedule, rather than adding a
     * separate DTO for the one field that differs). Everything else,
     * including vehicleId/driverId, is a full replace: omitting one clears
     * the existing assignment, same as any other field on this PUT.
     */
    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public ScheduleResponse updateSchedule(String operatorUsername, Long scheduleId, ScheduleRequest request) {
        User caller = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .filter(candidate -> tenantScope.isSameTenant(candidate.getRoute().getOperator(), caller))
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));

        Vehicle vehicle = resolveVehicle(caller, request.vehicleId());
        Driver driver = resolveDriver(caller, request.driverId());
        requireNoVehicleConflict(vehicle, scheduleId, request.departureTime(), request.arrivalTime());
        requireNoDriverConflict(driver, scheduleId, request.departureTime(), request.arrivalTime());

        schedule.setDepartureTime(request.departureTime());
        schedule.setArrivalTime(request.arrivalTime());
        schedule.setBaseFare(request.baseFare());
        schedule.setCurrency(request.currency());
        schedule.setVehicle(vehicle);
        schedule.setDriver(driver);
        if (request.status() != null) {
            schedule.setStatus(request.status());
        }

        auditService.record(operatorUsername, "SCHEDULE_UPDATED", "SCHEDULE", schedule.getId(),
                "status=" + schedule.getStatus() + " departureTime=" + schedule.getDepartureTime());
        return scheduleMapper.toResponse(schedule);
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional(readOnly = true)
    public List<ScheduleResponse> listSchedulesForRoute(String operatorUsername, Long routeId) {
        User caller = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));
        Route route = routeRepository.findById(routeId)
                .filter(candidate -> tenantScope.isSameTenant(candidate.getOperator(), caller))
                .orElseThrow(() -> new RouteNotFoundException(routeId));

        return scheduleRepository.findByRouteId(route.getId()).stream()
                .map(scheduleMapper::toResponse)
                .toList();
    }

    private Vehicle resolveVehicle(User caller, Long vehicleId) {
        if (vehicleId == null) {
            return null;
        }
        return vehicleRepository.findById(vehicleId)
                .filter(candidate -> tenantScope.isSameTenant(candidate.getOperator(), caller))
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
    }

    private Driver resolveDriver(User caller, Long driverId) {
        if (driverId == null) {
            return null;
        }
        return driverRepository.findById(driverId)
                .filter(candidate -> tenantScope.isSameTenant(candidate.getOperator(), caller))
                .orElseThrow(() -> new DriverNotFoundException(driverId));
    }

    private void requireNoVehicleConflict(Vehicle vehicle, Long excludeScheduleId, Instant departureTime, Instant arrivalTime) {
        if (vehicle == null) {
            return;
        }
        List<Schedule> overlapping = scheduleRepository.findOverlappingByVehicle(
                vehicle.getId(), excludeScheduleId, departureTime, arrivalTime);
        if (!overlapping.isEmpty()) {
            throw new ScheduleConflictException("Vehicle", vehicle.getId(), overlapping.get(0).getId());
        }
    }

    private void requireNoDriverConflict(Driver driver, Long excludeScheduleId, Instant departureTime, Instant arrivalTime) {
        if (driver == null) {
            return;
        }
        List<Schedule> overlapping = scheduleRepository.findOverlappingByDriver(
                driver.getId(), excludeScheduleId, departureTime, arrivalTime);
        if (!overlapping.isEmpty()) {
            throw new ScheduleConflictException("Driver", driver.getId(), overlapping.get(0).getId());
        }
    }
}
