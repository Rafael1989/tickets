package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.SeatRequest;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.dto.SeatUpdateRequest;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.mapper.SeatMapper;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SeatManagementServiceImplTest {

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private SeatMapper seatMapper;

    @InjectMocks
    private SeatManagementServiceImpl seatManagementService;

    private static Schedule scheduleOwnedBy(long id, String operatorUsername) {
        Route route = Route.builder().operator(User.builder().username(operatorUsername).build()).build();
        return Schedule.builder().id(id).route(route).build();
    }

    @Test
    void addSeat_whenScheduleOwnedByOperator_savesAndDefaultsStatusWhenAbsent() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        SeatRequest request = new SeatRequest(1L, "1A", "economy", null, new BigDecimal("1.000"));
        Seat mapped = Seat.builder().schedule(schedule).seatNumber("1A").build();
        Seat saved = Seat.builder().id(5L).schedule(schedule).status(SeatStatus.AVAILABLE).build();
        SeatResponse response = new SeatResponse(5L, 1L, "1A", "economy", SeatStatus.AVAILABLE, new BigDecimal("1.000"));

        given(scheduleRepository.findById(1L)).willReturn(Optional.of(schedule));
        given(seatMapper.toEntity(request, schedule)).willReturn(mapped);
        given(seatRepository.save(mapped)).willReturn(saved);
        given(seatMapper.toResponse(saved)).willReturn(response);

        SeatResponse result = seatManagementService.addSeat("operator1", request);

        assertThat(result).isEqualTo(response);
        assertThat(mapped.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void addSeat_whenScheduleBelongsToDifferentOperator_throwsScheduleNotFoundException() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        given(scheduleRepository.findById(1L)).willReturn(Optional.of(schedule));
        SeatRequest request = new SeatRequest(1L, "1A", "economy", null, new BigDecimal("1.000"));

        assertThatThrownBy(() -> seatManagementService.addSeat("mallory", request))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void updateSeat_whenOwnedByOperator_updatesStatusAndPriceModifier() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        Seat seat = Seat.builder().id(5L).schedule(schedule).status(SeatStatus.AVAILABLE)
                .priceModifier(BigDecimal.ONE).build();
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.HELD, new BigDecimal("1.500"));
        given(seatRepository.findById(5L)).willReturn(Optional.of(seat));
        given(seatMapper.toResponse(seat)).willReturn(
                new SeatResponse(5L, 1L, null, null, SeatStatus.HELD, new BigDecimal("1.500")));

        SeatResponse result = seatManagementService.updateSeat("operator1", 5L, request);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getPriceModifier()).isEqualByComparingTo("1.500");
        assertThat(result.status()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void updateSeat_whenSeatBelongsToDifferentOperator_throwsSeatNotFoundException() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        Seat seat = Seat.builder().id(5L).schedule(schedule).build();
        given(seatRepository.findById(5L)).willReturn(Optional.of(seat));
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.HELD, BigDecimal.ONE);

        assertThatThrownBy(() -> seatManagementService.updateSeat("mallory", 5L, request))
                .isInstanceOf(SeatNotFoundException.class);
    }

    @Test
    void updateSeat_whenMissing_throwsSeatNotFoundException() {
        given(seatRepository.findById(99L)).willReturn(Optional.empty());
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.HELD, BigDecimal.ONE);

        assertThatThrownBy(() -> seatManagementService.updateSeat("operator1", 99L, request))
                .isInstanceOf(SeatNotFoundException.class);
    }
}
