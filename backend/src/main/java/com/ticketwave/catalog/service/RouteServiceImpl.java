package com.ticketwave.catalog.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.exception.RouteNotFoundException;
import com.ticketwave.catalog.mapper.RouteMapper;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.catalog.security.TenantScope;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
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
    private final TenantScope tenantScope;

    public RouteServiceImpl(
            RouteRepository routeRepository,
            UserRepository userRepository,
            RouteMapper routeMapper,
            AuditService auditService,
            TenantScope tenantScope
    ) {
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
        this.routeMapper = routeMapper;
        this.auditService = auditService;
        this.tenantScope = tenantScope;
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

    /**
     * Evicts both caches wholesale rather than targeting just this route's
     * schedules: a route's static fields (type/origin/destination/venue) are
     * denormalized into every one of its schedules' cached
     * ScheduleStaticInfo, and finding just those entries would need an extra
     * query for no real benefit at this scale — route edits are an
     * infrequent operator action, not a hot path.
     */
    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    @CacheEvict(cacheNames = {"scheduleSearchIds", "scheduleStaticInfo"}, allEntries = true)
    public RouteResponse updateRoute(String operatorUsername, Long routeId, RouteRequest request) {
        User caller = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));
        Route route = routeRepository.findById(routeId)
                .filter(candidate -> tenantScope.isSameTenant(candidate.getOperator(), caller))
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

    /**
     * "Mine" broadens to "my partner's" when the caller belongs to one — a
     * partner company's staff share one route inventory rather than each
     * login only ever seeing what it personally created.
     */
    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional(readOnly = true)
    public List<RouteResponse> listMyRoutes(String operatorUsername) {
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));

        List<Route> routes = operator.getPartner() != null
                ? routeRepository.findByOperatorPartnerId(operator.getPartner().getId())
                : routeRepository.findByOperatorId(operator.getId());

        return routes.stream()
                .map(routeMapper::toResponse)
                .toList();
    }
}
