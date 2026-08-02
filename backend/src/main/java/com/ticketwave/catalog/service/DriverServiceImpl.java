package com.ticketwave.catalog.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.dto.DriverRequest;
import com.ticketwave.catalog.dto.DriverResponse;
import com.ticketwave.catalog.entity.Driver;
import com.ticketwave.catalog.mapper.DriverMapper;
import com.ticketwave.catalog.repository.DriverRepository;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final DriverMapper driverMapper;
    private final AuditService auditService;

    public DriverServiceImpl(
            DriverRepository driverRepository,
            UserRepository userRepository,
            DriverMapper driverMapper,
            AuditService auditService
    ) {
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
        this.driverMapper = driverMapper;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public DriverResponse createDriver(String operatorUsername, DriverRequest request) {
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));

        Driver saved = driverRepository.save(driverMapper.toEntity(request, operator));
        auditService.record(operatorUsername, "DRIVER_CREATED", "DRIVER", saved.getId(),
                "fullName=" + saved.getFullName());
        return driverMapper.toResponse(saved);
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional(readOnly = true)
    public List<DriverResponse> listMyDrivers(String operatorUsername) {
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));

        List<Driver> drivers = operator.getPartner() != null
                ? driverRepository.findByOperatorPartnerId(operator.getPartner().getId())
                : driverRepository.findByOperatorId(operator.getId());

        return drivers.stream()
                .map(driverMapper::toResponse)
                .toList();
    }
}
