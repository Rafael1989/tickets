package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercised indirectly through RouteServiceImplTest too, but kept here as a
 * direct unit test of the mapping itself.
 */
class RouteMapperTest {

    private final RouteMapper mapper = new RouteMapperImpl();

    @Test
    void toEntity_setsOperatorAndCopiesRequestFields() {
        User operator = User.builder().id(1L).build();
        RouteRequest request = new RouteRequest(RouteType.BUS, "NYC", "Boston", null, 90);

        Route route = mapper.toEntity(request, operator);

        assertThat(route.getOperator()).isEqualTo(operator);
        assertThat(route.getType()).isEqualTo(RouteType.BUS);
        assertThat(route.getOrigin()).isEqualTo("NYC");
        assertThat(route.getDestination()).isEqualTo("Boston");
        assertThat(route.getVenue()).isNull();
        assertThat(route.getDurationMinutes()).isEqualTo(90);
        assertThat(route.getId()).isNull();
    }

    @Test
    void toEntity_whenBothArgumentsAreNull_returnsNull() {
        assertThat(mapper.toEntity(null, null)).isNull();
    }

    @Test
    void toEntity_whenRequestIsNull_stillSetsOperatorOnly() {
        User operator = User.builder().id(1L).build();

        Route route = mapper.toEntity(null, operator);

        assertThat(route.getOperator()).isEqualTo(operator);
        assertThat(route.getType()).isNull();
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_whenOperatorIsNull_leavesOperatorIdNull() {
        Route route = Route.builder().id(10L).build();

        RouteResponse response = mapper.toResponse(route);

        assertThat(response.operatorId()).isNull();
    }

    @Test
    void toResponse_flattensOperatorId() {
        Route route = Route.builder()
                .id(10L)
                .operator(User.builder().id(1L).build())
                .type(RouteType.EVENT)
                .venue("Arena")
                .build();

        RouteResponse response = mapper.toResponse(route);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.operatorId()).isEqualTo(1L);
        assertThat(response.type()).isEqualTo(RouteType.EVENT);
        assertThat(response.venue()).isEqualTo("Arena");
    }
}
