package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.dto.ScheduleRequest;
import com.ticketwave.catalog.dto.ScheduleResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
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

        Schedule schedule = mapper.toEntity(request, route);

        assertThat(schedule.getRoute()).isEqualTo(route);
        assertThat(schedule.getDepartureTime()).isEqualTo(Instant.parse("2026-08-01T10:00:00Z"));
        assertThat(schedule.getArrivalTime()).isEqualTo(Instant.parse("2026-08-01T12:00:00Z"));
        assertThat(schedule.getBaseFare()).isEqualByComparingTo("25.00");
        assertThat(schedule.getCurrency()).isEqualTo("USD");
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
        assertThat(schedule.getId()).isNull();
    }

    @Test
    void toEntity_whenBothArgumentsAreNull_returnsNull() {
        assertThat(mapper.toEntity(null, null)).isNull();
    }

    @Test
    void toEntity_whenRequestIsNull_stillSetsRouteOnly() {
        Route route = Route.builder().id(10L).build();

        Schedule schedule = mapper.toEntity(null, route);

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
    void toResponse_flattensRouteId() {
        Schedule schedule = Schedule.builder()
                .id(1L)
                .route(Route.builder().id(10L).build())
                .baseFare(new BigDecimal("25.00"))
                .currency("USD")
                .status(ScheduleStatus.SCHEDULED)
                .build();

        ScheduleResponse response = mapper.toResponse(schedule);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.routeId()).isEqualTo(10L);
        assertThat(response.baseFare()).isEqualByComparingTo("25.00");
        assertThat(response.status()).isEqualTo(ScheduleStatus.SCHEDULED);
    }
}
