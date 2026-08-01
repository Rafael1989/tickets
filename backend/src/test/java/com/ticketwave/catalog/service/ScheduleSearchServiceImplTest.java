package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.dto.ScheduleSearchResult;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.mapper.SeatMapper;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.ScheduleSeatCount;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.pricing.service.PricingService;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.repository.UserRepository;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
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
class ScheduleSearchServiceImplTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SeatMapper seatMapper;

    @Mock
    private PricingService pricingService;

    @Mock
    private Clock clock;

    @InjectMocks
    private ScheduleSearchServiceImpl searchService;

    private static Route route(long id, RouteType type, String origin, String destination, String venue) {
        return Route.builder()
                .id(id)
                .type(type)
                .origin(origin)
                .destination(destination)
                .venue(venue)
                .build();
    }

    private static Schedule schedule(long id, Route route, Instant departure, BigDecimal fare) {
        return Schedule.builder()
                .id(id)
                .route(route)
                .departureTime(departure)
                .arrivalTime(departure.plusSeconds(3600))
                .baseFare(fare)
                .currency("USD")
                .status(ScheduleStatus.SCHEDULED)
                .build();
    }

    private record TestSeatCount(Long scheduleId, long availableCount) implements ScheduleSeatCount {
        @Override
        public Long getScheduleId() {
            return scheduleId;
        }

        @Override
        public long getAvailableCount() {
            return availableCount;
        }
    }

    @Test
    void search_mapsEachScheduleWithItsRealTimeAvailableSeatCount() {
        Route route1 = route(10L, RouteType.BUS, "NYC", "Boston", null);
        Route route2 = route(11L, RouteType.EVENT, null, null, "Arena");
        Schedule schedule1 = schedule(1L, route1, Instant.parse("2026-08-01T10:00:00Z"), new BigDecimal("25.00"));
        Schedule schedule2 = schedule(2L, route2, Instant.parse("2026-08-02T18:00:00Z"), new BigDecimal("50.00"));

        given(clock.instant()).willReturn(Instant.parse("2026-01-01T00:00:00Z"));
        given(scheduleRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(schedule1, schedule2));
        given(seatRepository.countAvailableGroupedByScheduleId(List.of(1L, 2L), SeatStatus.AVAILABLE))
                .willReturn(List.of(new TestSeatCount(1L, 12L)));

        List<ScheduleSearchResult> results = searchService.search(
                new ScheduleSearchCriteria(null, "NYC", "Boston", null, null));

        assertThat(results).hasSize(2);

        ScheduleSearchResult first = results.get(0);
        assertThat(first.scheduleId()).isEqualTo(1L);
        assertThat(first.routeId()).isEqualTo(10L);
        assertThat(first.origin()).isEqualTo("NYC");
        assertThat(first.destination()).isEqualTo("Boston");
        assertThat(first.availableSeats()).isEqualTo(12L);

        ScheduleSearchResult second = results.get(1);
        assertThat(second.scheduleId()).isEqualTo(2L);
        assertThat(second.venue()).isEqualTo("Arena");
        assertThat(second.availableSeats()).isEqualTo(0L);
    }

    @Test
    void search_sortsByDepartureTimeAscending() {
        given(clock.instant()).willReturn(Instant.parse("2026-01-01T00:00:00Z"));
        given(scheduleRepository.findAll(any(Specification.class), any(Sort.class))).willReturn(List.of());

        searchService.search(new ScheduleSearchCriteria(null, null, null, null, null));

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(scheduleRepository).findAll(any(Specification.class), sortCaptor.capture());

        Sort.Order order = sortCaptor.getValue().getOrderFor("departureTime");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void getAvailableSeatCount_whenScheduleExists_returnsCountFromRepository() {
        given(scheduleRepository.existsById(1L)).willReturn(true);
        given(seatRepository.countByScheduleIdAndStatus(1L, SeatStatus.AVAILABLE)).willReturn(7L);

        long count = searchService.getAvailableSeatCount(1L);

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void getAvailableSeatCount_whenScheduleMissing_throwsScheduleNotFoundExceptionWithoutQueryingSeats() {
        given(scheduleRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> searchService.getAvailableSeatCount(99L))
                .isInstanceOf(ScheduleNotFoundException.class);

        verify(seatRepository, never()).countByScheduleIdAndStatus(eq(99L), any());
    }

    @Test
    void getScheduleDetails_whenScheduleExists_returnsItsSearchResultShape() {
        Route route = route(10L, RouteType.BUS, "NYC", "Boston", null);
        Schedule schedule = schedule(1L, route, Instant.parse("2026-08-01T10:00:00Z"), new BigDecimal("25.00"));
        given(scheduleRepository.findById(1L)).willReturn(Optional.of(schedule));
        given(seatRepository.countByScheduleIdAndStatus(1L, SeatStatus.AVAILABLE)).willReturn(4L);

        ScheduleSearchResult result = searchService.getScheduleDetails(1L);

        assertThat(result.scheduleId()).isEqualTo(1L);
        assertThat(result.origin()).isEqualTo("NYC");
        assertThat(result.availableSeats()).isEqualTo(4L);
    }

    @Test
    void getScheduleDetails_whenScheduleMissing_throwsScheduleNotFoundException() {
        given(scheduleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> searchService.getScheduleDetails(99L))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void getSeatsForSchedule_whenScheduleExists_returnsAllSeatsRegardlessOfStatusWithEstimatedFare() {
        Route route = route(10L, RouteType.BUS, "NYC", "Boston", null);
        Schedule schedule = schedule(1L, route, Instant.parse("2026-08-01T10:00:00Z"), new BigDecimal("20.00"));
        Seat available = Seat.builder().id(1L).schedule(schedule).status(SeatStatus.AVAILABLE).build();
        Seat booked = Seat.builder().id(2L).schedule(schedule).status(SeatStatus.BOOKED).build();
        given(scheduleRepository.findById(1L)).willReturn(Optional.of(schedule));
        given(seatRepository.findByScheduleId(1L)).willReturn(List.of(available, booked));
        given(seatMapper.toResponse(available)).willReturn(
                new SeatResponse(1L, 1L, "1A", "economy", SeatStatus.AVAILABLE, BigDecimal.ONE, null, null, false));
        given(seatMapper.toResponse(booked)).willReturn(
                new SeatResponse(2L, 1L, "1B", "economy", SeatStatus.BOOKED, BigDecimal.ONE, null, null, false));
        given(pricingService.calculateSeatFare(schedule, available)).willReturn(new BigDecimal("20.00"));
        given(pricingService.calculateSeatFare(schedule, booked)).willReturn(new BigDecimal("20.00"));

        List<SeatResponse> seats = searchService.getSeatsForSchedule(1L, null);

        assertThat(seats).hasSize(2);
        assertThat(seats).extracting(SeatResponse::status)
                .containsExactly(SeatStatus.AVAILABLE, SeatStatus.BOOKED);
        assertThat(seats).extracting(SeatResponse::estimatedFare)
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("20.00"));
        assertThat(seats).extracting(SeatResponse::heldByMe).containsExactly(false, false);
    }

    @Test
    void getSeatsForSchedule_whenScheduleMissing_throwsScheduleNotFoundException() {
        given(scheduleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> searchService.getSeatsForSchedule(99L, null))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void getSeatsForSchedule_whenCallerHoldsASeat_marksOnlyThatSeatHeldByMe() {
        Route route = route(10L, RouteType.BUS, "NYC", "Boston", null);
        Schedule schedule = schedule(1L, route, Instant.parse("2026-08-01T10:00:00Z"), new BigDecimal("20.00"));
        com.ticketwave.user.entity.User caller = User.builder().id(7L).username("alice").build();
        com.ticketwave.user.entity.User someoneElse = User.builder().id(8L).username("bob").build();
        Seat myHold = Seat.builder().id(1L).schedule(schedule).status(SeatStatus.HELD).heldBy(caller).build();
        Seat othersHold = Seat.builder().id(2L).schedule(schedule).status(SeatStatus.HELD).heldBy(someoneElse).build();

        given(scheduleRepository.findById(1L)).willReturn(Optional.of(schedule));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(caller));
        given(seatRepository.findByScheduleId(1L)).willReturn(List.of(myHold, othersHold));
        given(seatMapper.toResponse(myHold)).willReturn(
                new SeatResponse(1L, 1L, "1A", "economy", SeatStatus.HELD, BigDecimal.ONE, null, null, false));
        given(seatMapper.toResponse(othersHold)).willReturn(
                new SeatResponse(2L, 1L, "1B", "economy", SeatStatus.HELD, BigDecimal.ONE, null, null, false));
        given(pricingService.calculateSeatFare(eq(schedule), any())).willReturn(new BigDecimal("20.00"));

        List<SeatResponse> seats = searchService.getSeatsForSchedule(1L, "alice");

        assertThat(seats).extracting(SeatResponse::id, SeatResponse::heldByMe)
                .containsExactly(
                        Tuple.tuple(1L, true),
                        Tuple.tuple(2L, false));
    }

    @Test
    void getSeatsForSchedule_whenUsernameIsNull_neverMarksAnySeatHeldByMeEvenIfHeld() {
        Route route = route(10L, RouteType.BUS, "NYC", "Boston", null);
        Schedule schedule = schedule(1L, route, Instant.parse("2026-08-01T10:00:00Z"), new BigDecimal("20.00"));
        com.ticketwave.user.entity.User someone = User.builder().id(7L).username("alice").build();
        Seat held = Seat.builder().id(1L).schedule(schedule).status(SeatStatus.HELD).heldBy(someone).build();

        given(scheduleRepository.findById(1L)).willReturn(Optional.of(schedule));
        given(seatRepository.findByScheduleId(1L)).willReturn(List.of(held));
        given(seatMapper.toResponse(held)).willReturn(
                new SeatResponse(1L, 1L, "1A", "economy", SeatStatus.HELD, BigDecimal.ONE, null, null, false));
        given(pricingService.calculateSeatFare(schedule, held)).willReturn(new BigDecimal("20.00"));

        List<SeatResponse> seats = searchService.getSeatsForSchedule(1L, null);

        assertThat(seats).extracting(SeatResponse::heldByMe).containsExactly(false);
        verify(userRepository, never()).findByUsername(any());
    }
}
