package com.ticketwave.pricing.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.exception.RouteNotFoundException;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.pricing.dto.FareRuleRequest;
import com.ticketwave.pricing.dto.FareRuleResponse;
import com.ticketwave.pricing.entity.FareRule;
import com.ticketwave.pricing.mapper.FareRuleMapper;
import com.ticketwave.pricing.repository.FareRuleRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FareRuleServiceImpl implements FareRuleService {

    private final RouteRepository routeRepository;
    private final FareRuleRepository fareRuleRepository;
    private final FareRuleMapper fareRuleMapper;
    private final AuditService auditService;

    public FareRuleServiceImpl(
            RouteRepository routeRepository,
            FareRuleRepository fareRuleRepository,
            FareRuleMapper fareRuleMapper,
            AuditService auditService
    ) {
        this.routeRepository = routeRepository;
        this.fareRuleRepository = fareRuleRepository;
        this.fareRuleMapper = fareRuleMapper;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public FareRuleResponse createFareRule(String operatorUsername, FareRuleRequest request) {
        Route route = requireOwnedRoute(operatorUsername, request.routeId());

        FareRule saved = fareRuleRepository.save(fareRuleMapper.toEntity(request, route));
        auditService.record(operatorUsername, "FARE_RULE_CREATED", "FARE_RULE", saved.getId(),
                "routeId=" + route.getId() + " seatClass=" + saved.getSeatClass() + " rate=" + saved.getSurchargeRate());
        return fareRuleMapper.toResponse(saved);
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public List<FareRuleResponse> bulkCreateFareRules(String operatorUsername, List<FareRuleRequest> requests) {
        // Resolve + ownership-check every distinct route up front, so a bad
        // row anywhere in the batch throws before anything is persisted.
        Map<Long, Route> routesById = new LinkedHashMap<>();
        for (FareRuleRequest request : requests) {
            routesById.computeIfAbsent(request.routeId(), id -> requireOwnedRoute(operatorUsername, id));
        }

        List<FareRule> entities = requests.stream()
                .map(request -> fareRuleMapper.toEntity(request, routesById.get(request.routeId())))
                .toList();
        List<FareRule> saved = fareRuleRepository.saveAll(entities);

        auditService.record(operatorUsername, "FARE_RULES_BULK_LOADED", "FARE_RULE", null,
                "count=" + saved.size());
        return saved.stream().map(fareRuleMapper::toResponse).toList();
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional(readOnly = true)
    public List<FareRuleResponse> listFareRulesForRoute(String operatorUsername, Long routeId) {
        Route route = requireOwnedRoute(operatorUsername, routeId);

        return fareRuleRepository.findByRouteId(route.getId()).stream()
                .map(fareRuleMapper::toResponse)
                .toList();
    }

    private Route requireOwnedRoute(String operatorUsername, Long routeId) {
        return routeRepository.findById(routeId)
                .filter(candidate -> candidate.getOperator().getUsername().equals(operatorUsername))
                .orElseThrow(() -> new RouteNotFoundException(routeId));
    }
}
