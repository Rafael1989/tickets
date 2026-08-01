package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.dto.ScheduleRequest;
import com.ticketwave.catalog.dto.ScheduleResponse;
import com.ticketwave.catalog.entity.Driver;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleMapperTest {

    private final ScheduleMapper mapper = new ScheduleMapperImpl();

    @Test
    void toEntity_setsRouteAndCopiesRequestFields() {
        Route route = Route.builder().id(10L).build();
        ScheduleRequest request = new ScheduleRequest(
                10L,
                Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-01T12:00:00Z"),
                new BigDecimal("25.00"),
                "USD",
                ScheduleStatus.SCHEDULED);

        Schedule schedule = mapper.toEntity(request, route, null, null);

        assertThat(schedule.getRoute()).isEqualTo(route);
        assertThat(schedule.getDepartureTime()).isEqualTo(Instant.parse("2026-08-01T10:00:00Z"));
        assertThat(schedule.getArrivalTime()).isEqualTo(Instant.parse("2026-08-01T12:00:00Z"));
        assertThat(schedule.getBaseFare()).isEqualByComparingTo("25.00");
        assertThat(schedule.getCurrency()).isEqualTo("USD");
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
        assertThat(schedule.getId()).isNull();
    }

    @Test
    void toEntity_setsVehicleAndDriverWhenProvided() {
        Route route = Route.builder().id(10L).build();
        Vehicle vehicle = Vehicle.builder().id(5L).build();
        Driver driver = Driver.builder().id(7L).build();
        ScheduleRequest request = new ScheduleRequest(
                10L, Instant.now(), Instant.now(), new BigDecimal("25.00"), "USD", null);

        Schedule schedule = mapper.toEntity(request, route, vehicle, driver);

        assertThat(schedule.getVehicle()).isEqualTo(vehicle);
        assertThat(schedule.getDriver()).isEqualTo(driver);
    }

    @Test
    void toEntity_whenAllArgumentsAreNull_returnsNull() {
        assertThat(mapper.toEntity(null, null, null, null)).isNull();
    }

    @Test
    void toEntity_whenRequestIsNull_stillSetsRouteOnly() {
        Route route = Route.builder().id(10L).build();

        Schedule schedule = mapper.toEntity(null, route, null, null);

        assertThat(schedule.getRoute()).isEqualTo(route);
        assertThat(schedule.getCurrency()).isNull();
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_whenRouteIsNull_leavesRouteIdNull() {
        Schedule schedule = Schedule.builder().id(1L).build();

        ScheduleResponse response = mapper.toResponse(schedule);

        assertThat(response.routeId()).isNull();
    }

    @Test
    void toResponse_flattensRouteIdVehicleIdAndDriverId() {
        Schedule schedule = Schedule.builder()
                .id(1L)
                .route(Route.builder().id(10L).build())
                .vehicle(Vehicle.builder().id(5L).build())
                .driver(Driver.builder().id(7L).build())
                .baseFare(new BigDecimal("25.00"))
                .currency("USD")
                .status(ScheduleStatus.SCHEDULED)
                .build();

        ScheduleResponse response = mapper.toResponse(schedule);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.routeId()).isEqualTo(10L);
        assertThat(response.vehicleId()).isEqualTo(5L);
        assertThat(response.driverId()).isEqualTo(7L);
        assertThat(response.baseFare()).isEqualByComparingTo("25.00");
        assertThat(response.status()).isEqualTo(ScheduleStatus.SCHEDULED);
    }
}
