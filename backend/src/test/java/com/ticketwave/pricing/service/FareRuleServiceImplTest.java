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
import com.ticketwave.catalog.security.TenantScope;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FareRuleServiceImplTest {

    @Mock
    private RouteRepository routeRepository;
    @Mock
    private FareRuleRepository fareRuleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FareRuleMapper fareRuleMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private TenantScope tenantScope;

    @InjectMocks
    private FareRuleServiceImpl fareRuleService;

    /**
     * Reproduces the pre-multi-tenant "exact same username" ownership check
     * through the new UserRepository/TenantScope collaborators, so every
     * existing test below keeps its original username-based semantics
     * without needing to stub these two on a per-test basis.
     */
    @BeforeEach
    void stubTenantResolutionByUsername() {
        org.mockito.Mockito.lenient().when(userRepository.findByUsername(any()))
                .thenAnswer(inv -> Optional.of(User.builder().username(inv.getArgument(0)).build()));
        org.mockito.Mockito.lenient().when(tenantScope.isSameTenant(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0, User.class).getUsername().equals(inv.getArgument(1, User.class).getUsername()));
    }

    private static Route route(long id, String operatorUsername) {
        return Route.builder().id(id).operator(User.builder().username(operatorUsername).build()).build();
    }

    private static FareRuleRequest request(long routeId) {
        return new FareRuleRequest(routeId, "business", Instant.now(), Instant.now().plusSeconds(3600), new BigDecimal("0.20"));
    }

    @Test
    void createFareRule_whenRouteOwnedByOperator_savesAndAudits() {
        Route route = route(1L, "operator1");
        FareRuleRequest request = request(1L);
        FareRule entity = FareRule.builder().route(route).seatClass("business").build();
        FareRule saved = FareRule.builder().id(9L).route(route).seatClass("business").build();
        FareRuleResponse response = new FareRuleResponse(9L, 1L, "business", request.validFrom(), request.validTo(), request.surchargeRate());

        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(fareRuleMapper.toEntity(request, route)).willReturn(entity);
        given(fareRuleRepository.save(entity)).willReturn(saved);
        given(fareRuleMapper.toResponse(saved)).willReturn(response);

        FareRuleResponse result = fareRuleService.createFareRule("operator1", request);

        assertThat(result).isEqualTo(response);
        verify(auditService).record(eq("operator1"), eq("FARE_RULE_CREATED"), eq("FARE_RULE"), any(), any());
    }

    @Test
    void createFareRule_whenRouteBelongsToDifferentOperator_throwsRouteNotFoundException() {
        Route route = route(1L, "operator1");
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));

        assertThatThrownBy(() -> fareRuleService.createFareRule("mallory", request(1L)))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void bulkCreateFareRules_allRowsOwnedByOperator_savesAllAndAuditsOnce() {
        Route route1 = route(1L, "operator1");
        Route route2 = route(2L, "operator1");
        FareRuleRequest req1 = request(1L);
        FareRuleRequest req2 = request(2L);
        FareRule entity1 = FareRule.builder().route(route1).seatClass("business").build();
        FareRule entity2 = FareRule.builder().route(route2).seatClass("business").build();
        FareRule saved1 = FareRule.builder().id(9L).route(route1).seatClass("business").build();
        FareRule saved2 = FareRule.builder().id(10L).route(route2).seatClass("business").build();

        given(routeRepository.findById(1L)).willReturn(Optional.of(route1));
        given(routeRepository.findById(2L)).willReturn(Optional.of(route2));
        given(fareRuleMapper.toEntity(req1, route1)).willReturn(entity1);
        given(fareRuleMapper.toEntity(req2, route2)).willReturn(entity2);
        given(fareRuleRepository.saveAll(List.of(entity1, entity2))).willReturn(List.of(saved1, saved2));
        given(fareRuleMapper.toResponse(saved1)).willReturn(
                new FareRuleResponse(9L, 1L, "business", req1.validFrom(), req1.validTo(), req1.surchargeRate()));
        given(fareRuleMapper.toResponse(saved2)).willReturn(
                new FareRuleResponse(10L, 2L, "business", req2.validFrom(), req2.validTo(), req2.surchargeRate()));

        List<FareRuleResponse> result = fareRuleService.bulkCreateFareRules("operator1", List.of(req1, req2));

        assertThat(result).hasSize(2);
        verify(auditService).record(eq("operator1"), eq("FARE_RULES_BULK_LOADED"), eq("FARE_RULE"), eq(null), any());
    }

    @Test
    void bulkCreateFareRules_whenAnyRowsRouteNotOwned_throwsAndPersistsNothing() {
        Route ownedRoute = route(1L, "operator1");
        Route otherOperatorsRoute = route(2L, "mallory");
        given(routeRepository.findById(1L)).willReturn(Optional.of(ownedRoute));
        given(routeRepository.findById(2L)).willReturn(Optional.of(otherOperatorsRoute));

        assertThatThrownBy(() -> fareRuleService.bulkCreateFareRules("operator1", List.of(request(1L), request(2L))))
                .isInstanceOf(RouteNotFoundException.class);

        verify(fareRuleRepository, never()).saveAll(any());
    }

    @Test
    void listFareRulesForRoute_whenOwnedByOperator_returnsMappedRules() {
        Route route = route(1L, "operator1");
        FareRule rule = FareRule.builder().id(9L).route(route).seatClass("business").build();
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(fareRuleRepository.findByRouteId(1L)).willReturn(List.of(rule));
        given(fareRuleMapper.toResponse(rule)).willReturn(
                new FareRuleResponse(9L, 1L, "business", Instant.now(), Instant.now().plusSeconds(3600), new BigDecimal("0.20")));

        List<FareRuleResponse> result = fareRuleService.listFareRulesForRoute("operator1", 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).seatClass()).isEqualTo("business");
    }

    @Test
    void listFareRulesForRoute_whenOwnedByDifferentOperator_throwsRouteNotFoundException() {
        Route route = route(1L, "operator1");
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));

        assertThatThrownBy(() -> fareRuleService.listFareRulesForRoute("mallory", 1L))
                .isInstanceOf(RouteNotFoundException.class);
    }
}
