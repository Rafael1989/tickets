package com.ticketwave.catalog.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.exception.RouteNotFoundException;
import com.ticketwave.catalog.mapper.RouteMapper;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RouteServiceImplTest {

    @Mock
    private RouteRepository routeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RouteMapper routeMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private RouteServiceImpl routeService;

    @Test
    void createRoute_resolvesOperatorFromUsernameAndSaves() {
        User operator = User.builder().id(1L).username("operator1").build();
        RouteRequest request = new RouteRequest(RouteType.BUS, "NYC", "Boston", null, 240);
        Route entity = Route.builder().operator(operator).type(RouteType.BUS).build();
        Route saved = Route.builder().id(5L).operator(operator).type(RouteType.BUS).build();
        RouteResponse response = new RouteResponse(5L, 1L, RouteType.BUS, "NYC", "Boston", null, 240);

        given(userRepository.findByUsername("operator1")).willReturn(Optional.of(operator));
        given(routeMapper.toEntity(request, operator)).willReturn(entity);
        given(routeRepository.save(entity)).willReturn(saved);
        given(routeMapper.toResponse(saved)).willReturn(response);

        RouteResponse result = routeService.createRoute("operator1", request);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void createRoute_whenOperatorMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());
        RouteRequest request = new RouteRequest(RouteType.BUS, "NYC", "Boston", null, 240);

        assertThatThrownBy(() -> routeService.createRoute("ghost", request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void listMyRoutes_returnsRoutesOwnedByTheOperator() {
        User operator = User.builder().id(1L).username("operator1").build();
        Route route = Route.builder().id(5L).operator(operator).type(RouteType.BUS).build();
        RouteResponse response = new RouteResponse(5L, 1L, RouteType.BUS, "NYC", "Boston", null, 240);

        given(userRepository.findByUsername("operator1")).willReturn(Optional.of(operator));
        given(routeRepository.findByOperatorId(1L)).willReturn(List.of(route));
        given(routeMapper.toResponse(route)).willReturn(response);

        List<RouteResponse> result = routeService.listMyRoutes("operator1");

        assertThat(result).containsExactly(response);
    }

    @Test
    void listMyRoutes_whenOperatorMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.listMyRoutes("ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateRoute_whenOwnedByOperator_updatesFieldsAndAudits() {
        User operator = User.builder().id(1L).username("operator1").build();
        Route route = Route.builder().id(5L).operator(operator).type(RouteType.BUS)
                .origin("NYC").destination("Boston").durationMinutes(240).build();
        RouteRequest request = new RouteRequest(RouteType.TRAIN, "Boston", "NYC", null, 200);
        given(routeRepository.findById(5L)).willReturn(Optional.of(route));
        given(routeMapper.toResponse(route)).willReturn(
                new RouteResponse(5L, 1L, RouteType.TRAIN, "Boston", "NYC", null, 200));

        RouteResponse result = routeService.updateRoute("operator1", 5L, request);

        assertThat(route.getType()).isEqualTo(RouteType.TRAIN);
        assertThat(route.getOrigin()).isEqualTo("Boston");
        assertThat(route.getDestination()).isEqualTo("NYC");
        assertThat(route.getDurationMinutes()).isEqualTo(200);
        assertThat(result.type()).isEqualTo(RouteType.TRAIN);
        verify(auditService).record(eq("operator1"), eq("ROUTE_UPDATED"), eq("ROUTE"), any(), any());
    }

    @Test
    void updateRoute_whenOwnedByDifferentOperator_throwsRouteNotFoundException() {
        User operator = User.builder().id(1L).username("operator1").build();
        Route route = Route.builder().id(5L).operator(operator).type(RouteType.BUS).build();
        given(routeRepository.findById(5L)).willReturn(Optional.of(route));
        RouteRequest request = new RouteRequest(RouteType.TRAIN, "Boston", "NYC", null, 200);

        assertThatThrownBy(() -> routeService.updateRoute("mallory", 5L, request))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void updateRoute_whenMissing_throwsRouteNotFoundException() {
        given(routeRepository.findById(99L)).willReturn(Optional.empty());
        RouteRequest request = new RouteRequest(RouteType.TRAIN, "Boston", "NYC", null, 200);

        assertThatThrownBy(() -> routeService.updateRoute("operator1", 99L, request))
                .isInstanceOf(RouteNotFoundException.class);
    }
}
