package com.ticketwave.catalog.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.exception.RouteNotFoundException;
import com.ticketwave.catalog.mapper.RouteMapper;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final UserRepository userRepository;
    private final RouteMapper routeMapper;
    private final AuditService auditService;

    public RouteServiceImpl(
            RouteRepository routeRepository,
            UserRepository userRepository,
            RouteMapper routeMapper,
            AuditService auditService
    ) {
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
        this.routeMapper = routeMapper;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public RouteResponse createRoute(String operatorUsername, RouteRequest request) {
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));

        Route route = routeRepository.save(routeMapper.toEntity(request, operator));
        auditService.record(operatorUsername, "ROUTE_CREATED", "ROUTE", route.getId(),
                "type=" + route.getType());
        return routeMapper.toResponse(route);
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public RouteResponse updateRoute(String operatorUsername, Long routeId, RouteRequest request) {
        Route route = routeRepository.findById(routeId)
                .filter(candidate -> candidate.getOperator().getUsername().equals(operatorUsername))
                .orElseThrow(() -> new RouteNotFoundException(routeId));

        route.setType(request.type());
        route.setOrigin(request.origin());
        route.setDestination(request.destination());
        route.setVenue(request.venue());
        route.setDurationMinutes(request.durationMinutes());

        auditService.record(operatorUsername, "ROUTE_UPDATED", "ROUTE", route.getId(),
                "type=" + route.getType());
        return routeMapper.toResponse(route);
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional(readOnly = true)
    public List<RouteResponse> listMyRoutes(String operatorUsername) {
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));

        return routeRepository.findByOperatorId(operator.getId()).stream()
                .map(routeMapper::toResponse)
                .toList();
    }
}
