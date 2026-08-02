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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleManagementServiceImplTest {

    @Mock
    private RouteRepository routeRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private DriverRepository driverRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ScheduleMapper scheduleMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private TenantScope tenantScope;

    @InjectMocks
    private ScheduleManagementServiceImpl scheduleManagementService;

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

    private static Vehicle vehicle(long id, String operatorUsername) {
        return Vehicle.builder().id(id).operator(User.builder().username(operatorUsername).build()).build();
    }

    private static Driver driver(long id, String operatorUsername) {
        return Driver.builder().id(id).operator(User.builder().username(operatorUsername).build()).build();
    }

    private static ScheduleResponse response(ScheduleStatus status) {
        return new ScheduleResponse(10L, 1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", status, null, null);
    }

    @Test
    void createSchedule_whenRouteOwnedByOperator_savesAndDefaultsStatusWhenAbsent() {
        Route route = route(1L, "operator1");
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null);
        Schedule mapped = Schedule.builder().route(route).baseFare(new BigDecimal("20.00")).currency("USD").build();
        Schedule saved = Schedule.builder().id(10L).route(route).status(ScheduleStatus.SCHEDULED).build();
        ScheduleResponse response = response(ScheduleStatus.SCHEDULED);

        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(scheduleMapper.toEntity(request, route, null, null)).willReturn(mapped);
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
        given(scheduleMapper.toEntity(request, route, null, null)).willReturn(mapped);
        given(scheduleRepository.save(mapped)).willReturn(mapped);
        given(scheduleMapper.toResponse(mapped)).willReturn(response(ScheduleStatus.DELAYED));

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

    @Test
    void createSchedule_withOwnedVehicleAndNoConflict_assignsIt() {
        Route route = route(1L, "operator1");
        Vehicle vehicle = vehicle(5L, "operator1");
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null, 5L, null);
        Schedule mapped = Schedule.builder().route(route).vehicle(vehicle).build();
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(vehicleRepository.findById(5L)).willReturn(Optional.of(vehicle));
        given(scheduleRepository.findOverlappingByVehicle(eq(5L), eq(0L), any(), any())).willReturn(List.of());
        given(scheduleMapper.toEntity(request, route, vehicle, null)).willReturn(mapped);
        given(scheduleRepository.save(mapped)).willReturn(mapped);
        given(scheduleMapper.toResponse(mapped)).willReturn(response(ScheduleStatus.SCHEDULED));

        scheduleManagementService.createSchedule("operator1", request);

        assertThat(mapped.getVehicle()).isEqualTo(vehicle);
    }

    @Test
    void createSchedule_withVehicleOwnedByDifferentOperator_throwsVehicleNotFoundException() {
        Route route = route(1L, "operator1");
        Vehicle vehicle = vehicle(5L, "someone-else");
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(vehicleRepository.findById(5L)).willReturn(Optional.of(vehicle));
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null, 5L, null);

        assertThatThrownBy(() -> scheduleManagementService.createSchedule("operator1", request))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void createSchedule_withVehicleAlreadyOverlappingAnotherSchedule_throwsScheduleConflictException() {
        Route route = route(1L, "operator1");
        Vehicle vehicle = vehicle(5L, "operator1");
        Schedule conflicting = Schedule.builder().id(20L).build();
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(vehicleRepository.findById(5L)).willReturn(Optional.of(vehicle));
        given(scheduleRepository.findOverlappingByVehicle(eq(5L), eq(0L), any(), any())).willReturn(List.of(conflicting));
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null, 5L, null);

        assertThatThrownBy(() -> scheduleManagementService.createSchedule("operator1", request))
                .isInstanceOf(ScheduleConflictException.class);

        verify(scheduleRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createSchedule_withDriverAlreadyOverlappingAnotherSchedule_throwsScheduleConflictException() {
        Route route = route(1L, "operator1");
        Driver driver = driver(7L, "operator1");
        Schedule conflicting = Schedule.builder().id(20L).build();
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(driverRepository.findById(7L)).willReturn(Optional.of(driver));
        given(scheduleRepository.findOverlappingByDriver(eq(7L), eq(0L), any(), any())).willReturn(List.of(conflicting));
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null, null, 7L);

        assertThatThrownBy(() -> scheduleManagementService.createSchedule("operator1", request))
                .isInstanceOf(ScheduleConflictException.class);
    }

    @Test
    void createSchedule_whenDriverMissing_throwsDriverNotFoundException() {
        Route route = route(1L, "operator1");
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(driverRepository.findById(7L)).willReturn(Optional.empty());
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null, null, 7L);

        assertThatThrownBy(() -> scheduleManagementService.createSchedule("operator1", request))
                .isInstanceOf(DriverNotFoundException.class);
    }

    @Test
    void updateSchedule_whenOwnedByOperator_updatesFieldsIgnoringRouteIdAndAudits() {
        Route route = route(1L, "operator1");
        Schedule schedule = Schedule.builder().id(10L).route(route)
                .departureTime(Instant.now()).arrivalTime(Instant.now().plusSeconds(3600))
                .baseFare(new BigDecimal("20.00")).currency("USD").status(ScheduleStatus.SCHEDULED).build();
        Instant newDeparture = Instant.now().plusSeconds(7200);
        Instant newArrival = Instant.now().plusSeconds(10800);
        ScheduleRequest request = new ScheduleRequest(999L, newDeparture, newArrival,
                new BigDecimal("35.00"), "EUR", ScheduleStatus.DELAYED);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleMapper.toResponse(schedule)).willReturn(
                new ScheduleResponse(10L, 1L, newDeparture, newArrival, new BigDecimal("35.00"), "EUR",
                        ScheduleStatus.DELAYED, null, null));

        ScheduleResponse result = scheduleManagementService.updateSchedule("operator1", 10L, request);

        assertThat(schedule.getDepartureTime()).isEqualTo(newDeparture);
        assertThat(schedule.getArrivalTime()).isEqualTo(newArrival);
        assertThat(schedule.getBaseFare()).isEqualByComparingTo("35.00");
        assertThat(schedule.getCurrency()).isEqualTo("EUR");
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.DELAYED);
        assertThat(schedule.getRoute()).isSameAs(route); // routeId in the request is ignored
        assertThat(result.status()).isEqualTo(ScheduleStatus.DELAYED);
        verify(auditService).record(eq("operator1"), eq("SCHEDULE_UPDATED"), eq("SCHEDULE"), any(), any());
    }

    @Test
    void updateSchedule_whenStatusNull_leavesExistingStatusUnchanged() {
        Route route = route(1L, "operator1");
        Schedule schedule = Schedule.builder().id(10L).route(route)
                .departureTime(Instant.now()).arrivalTime(Instant.now().plusSeconds(3600))
                .baseFare(new BigDecimal("20.00")).currency("USD").status(ScheduleStatus.DELAYED).build();
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleMapper.toResponse(schedule)).willReturn(response(ScheduleStatus.DELAYED));

        scheduleManagementService.updateSchedule("operator1", 10L, request);

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.DELAYED);
    }

    @Test
    void updateSchedule_omittingPreviouslyAssignedVehicle_clearsIt() {
        Route route = route(1L, "operator1");
        Vehicle vehicle = vehicle(5L, "operator1");
        Schedule schedule = Schedule.builder().id(10L).route(route).vehicle(vehicle)
                .departureTime(Instant.now()).arrivalTime(Instant.now().plusSeconds(3600))
                .baseFare(new BigDecimal("20.00")).currency("USD").status(ScheduleStatus.SCHEDULED).build();
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null, null, null);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleMapper.toResponse(schedule)).willReturn(response(ScheduleStatus.SCHEDULED));

        scheduleManagementService.updateSchedule("operator1", 10L, request);

        assertThat(schedule.getVehicle()).isNull();
    }

    @Test
    void updateSchedule_withVehicleOverlappingAnotherSchedule_excludesItselfButThrowsOnOthers() {
        Route route = route(1L, "operator1");
        Vehicle vehicle = vehicle(5L, "operator1");
        Schedule schedule = Schedule.builder().id(10L).route(route)
                .departureTime(Instant.now()).arrivalTime(Instant.now().plusSeconds(3600))
                .baseFare(new BigDecimal("20.00")).currency("USD").status(ScheduleStatus.SCHEDULED).build();
        Schedule conflicting = Schedule.builder().id(20L).build();
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null, 5L, null);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(vehicleRepository.findById(5L)).willReturn(Optional.of(vehicle));
        given(scheduleRepository.findOverlappingByVehicle(eq(5L), eq(10L), any(), any())).willReturn(List.of(conflicting));

        assertThatThrownBy(() -> scheduleManagementService.updateSchedule("operator1", 10L, request))
                .isInstanceOf(ScheduleConflictException.class);
    }

    @Test
    void updateSchedule_whenOwnedByDifferentOperator_throwsScheduleNotFoundException() {
        Route route = route(1L, "operator1");
        Schedule schedule = Schedule.builder().id(10L).route(route).build();
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null);

        assertThatThrownBy(() -> scheduleManagementService.updateSchedule("mallory", 10L, request))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void updateSchedule_whenMissing_throwsScheduleNotFoundException() {
        given(scheduleRepository.findById(99L)).willReturn(Optional.empty());
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null);

        assertThatThrownBy(() -> scheduleManagementService.updateSchedule("operator1", 99L, request))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void listSchedulesForRoute_whenOwnedByOperator_returnsMappedSchedules() {
        Route route = route(1L, "operator1");
        Schedule schedule = Schedule.builder().id(10L).route(route).status(ScheduleStatus.SCHEDULED).build();
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));
        given(scheduleRepository.findByRouteId(1L)).willReturn(List.of(schedule));
        given(scheduleMapper.toResponse(schedule)).willReturn(response(ScheduleStatus.SCHEDULED));

        var result = scheduleManagementService.listSchedulesForRoute("operator1", 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
    }

    @Test
    void listSchedulesForRoute_whenOwnedByDifferentOperator_throwsRouteNotFoundException() {
        Route route = route(1L, "operator1");
        given(routeRepository.findById(1L)).willReturn(Optional.of(route));

        assertThatThrownBy(() -> scheduleManagementService.listSchedulesForRoute("mallory", 1L))
                .isInstanceOf(RouteNotFoundException.class);
    }
}
