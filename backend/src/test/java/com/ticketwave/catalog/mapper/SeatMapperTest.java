package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.dto.SeatRequest;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SeatMapperTest {

    private final SeatMapper mapper = new SeatMapperImpl();

    @Test
    void toEntity_setsScheduleAndCopiesRequestFields_ignoresHeldUntil() {
        Schedule schedule = Schedule.builder().id(1L).build();
        SeatRequest request = new SeatRequest(1L, "1A", "economy", SeatStatus.AVAILABLE, new BigDecimal("1.000"));

        Seat seat = mapper.toEntity(request, schedule);

        assertThat(seat.getSchedule()).isEqualTo(schedule);
        assertThat(seat.getSeatNumber()).isEqualTo("1A");
        assertThat(seat.getSeatClass()).isEqualTo("economy");
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getPriceModifier()).isEqualByComparingTo("1.000");
        assertThat(seat.getId()).isNull();
        assertThat(seat.getHeldUntil()).isNull();
    }

    @Test
    void toEntity_whenBothArgumentsAreNull_returnsNull() {
        assertThat(mapper.toEntity(null, null)).isNull();
    }

    @Test
    void toEntity_whenRequestIsNull_stillSetsScheduleOnly() {
        Schedule schedule = Schedule.builder().id(1L).build();

        Seat seat = mapper.toEntity(null, schedule);

        assertThat(seat.getSchedule()).isEqualTo(schedule);
        assertThat(seat.getSeatNumber()).isNull();
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_whenScheduleIsNull_leavesScheduleIdNull() {
        Seat seat = Seat.builder().id(2L).build();

        SeatResponse response = mapper.toResponse(seat);

        assertThat(response.scheduleId()).isNull();
    }

    @Test
    void toResponse_flattensScheduleId() {
        Seat seat = Seat.builder()
                .id(2L)
                .schedule(Schedule.builder().id(1L).build())
                .seatNumber("1A")
                .seatClass("economy")
                .status(SeatStatus.BOOKED)
                .priceModifier(new BigDecimal("1.500"))
                .build();

        SeatResponse response = mapper.toResponse(seat);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.scheduleId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(SeatStatus.BOOKED);
        assertThat(response.priceModifier()).isEqualByComparingTo("1.500");
    }

    @Test
    void toResponse_mapsHeldUntilButLeavesEstimatedFareAndHeldByMeForTheServiceLayerToFill() {
        // estimatedFare needs the Schedule + PricingService, and heldByMe
        // needs the caller's identity — neither is derivable from a Seat
        // alone, so ScheduleSearchServiceImpl overlays them after this call.
        Instant heldUntil = Instant.parse("2026-01-01T00:00:00Z");
        Seat seat = Seat.builder().id(2L).status(SeatStatus.HELD).heldUntil(heldUntil).build();

        SeatResponse response = mapper.toResponse(seat);

        assertThat(response.heldUntil()).isEqualTo(heldUntil);
        assertThat(response.estimatedFare()).isNull();
        assertThat(response.heldByMe()).isFalse();
    }
}
