package com.ticketwave.catalog.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.dto.VehicleRequest;
import com.ticketwave.catalog.dto.VehicleResponse;
import com.ticketwave.catalog.entity.Vehicle;
import com.ticketwave.catalog.mapper.VehicleMapper;
import com.ticketwave.catalog.repository.VehicleRepository;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * listMyVehicles broadens to "my partner's" when the caller belongs to one —
 * see RouteServiceImpl.listMyRoutes for the same rationale.
 */

@Service
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final VehicleMapper vehicleMapper;
    private final AuditService auditService;

    public VehicleServiceImpl(
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            VehicleMapper vehicleMapper,
            AuditService auditService
    ) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.vehicleMapper = vehicleMapper;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public VehicleResponse createVehicle(String operatorUsername, VehicleRequest request) {
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));

        Vehicle saved = vehicleRepository.save(vehicleMapper.toEntity(request, operator));
        auditService.record(operatorUsername, "VEHICLE_CREATED", "VEHICLE", saved.getId(),
                "identifier=" + saved.getIdentifier());
        return vehicleMapper.toResponse(saved);
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional(readOnly = true)
    public List<VehicleResponse> listMyVehicles(String operatorUsername) {
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));

        List<Vehicle> vehicles = operator.getPartner() != null
                ? vehicleRepository.findByOperatorPartnerId(operator.getPartner().getId())
                : vehicleRepository.findByOperatorId(operator.getId());

        return vehicles.stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }
}
