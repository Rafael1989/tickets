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
import com.ticketwave.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScheduleManagementServiceImplTest {

    @Mock
    private RouteRepository routeRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ScheduleMapper scheduleMapper;

    @InjectMocks
    private ScheduleManagementServiceImpl scheduleManagementService;

    private static Route route(long id, String operatorUsername) {
        return Route.builder().id(id).operator(User.builder().username(operatorUsername).build()).build();
    }

    @Test
    void createSchedule_whenRouteOwnedByOperator_savesAndDefaultsStatusWhenAbsent() {
        Route route = route(1L, "operator1");
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null);
        Schedule mapped = Schedule.builder().route(route).baseFare(new BigDecimal("20.00")).currency("USD").build();
        Schedule saved = Schedule.builder().id(10L).route(route).status(ScheduleStatus.SCHEDULED).build();
        ScheduleResponse response = new ScheduleResponse(10L, 1L, request.departureTime(), request.arrivalTime(),
                new BigDecimal("20.00"), "USD", ScheduleStatus.SCHEDULED);

        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(scheduleMapper.toEntity(request, route)).willReturn(mapped);
        given(scheduleRepository.save(mapped)).willReturn(saved);
        given(scheduleMapper.toResponse(saved)).willReturn(response);

        ScheduleResponse result = scheduleManagementService.createSchedule("operator1", request);

        assertThat(result).isEqualTo(response);
        assertThat(mapped.getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
    }

    @Test
    void createSchedule_whenStatusExplicitlyProvided_leavesItUnchanged() {
        Route route = route(1L, "operator1");
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", ScheduleStatus.DELAYED);
        Schedule mapped = Schedule.builder().route(route).status(ScheduleStatus.DELAYED).build();
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(scheduleMapper.toEntity(request, route)).willReturn(mapped);
        given(scheduleRepository.save(mapped)).willReturn(mapped);
        given(scheduleMapper.toResponse(mapped)).willReturn(
                new ScheduleResponse(10L, 1L, request.departureTime(), request.arrivalTime(),
                        new BigDecimal("20.00"), "USD", ScheduleStatus.DELAYED));

        scheduleManagementService.createSchedule("operator1", request);

        assertThat(mapped.getStatus()).isEqualTo(ScheduleStatus.DELAYED);
    }

    @Test
    void createSchedule_whenRouteBelongsToDifferentOperator_throwsRouteNotFoundException() {
        Route route = route(1L, "operator1");
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null);

        assertThatThrownBy(() -> scheduleManagementService.createSchedule("mallory", request))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void createSchedule_whenRouteMissing_throwsRouteNotFoundException() {
        given(routeRepository.findById(99L)).willReturn(Optional.empty());
        ScheduleRequest request = new ScheduleRequest(99L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null);

        assertThatThrownBy(() -> scheduleManagementService.createSchedule("operator1", request))
                .isInstanceOf(RouteNotFoundException.class);
    }
}
