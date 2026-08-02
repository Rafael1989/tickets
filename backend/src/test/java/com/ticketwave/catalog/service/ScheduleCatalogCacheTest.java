package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.dto.ScheduleSortBy;
import com.ticketwave.catalog.dto.ScheduleStaticInfo;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.catalog.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleCatalogCacheTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @InjectMocks
    private ScheduleCatalogCache scheduleCatalogCache;

    private static Route route(long id, RouteType type, String origin, String destination, String venue) {
        return Route.builder().id(id).type(type).origin(origin).destination(destination).venue(venue).build();
    }

    private static Schedule schedule(long id, Route route, Instant departure, BigDecimal fare) {
        return Schedule.builder()
                .id(id).route(route).departureTime(departure).arrivalTime(departure.plusSeconds(3600))
                .baseFare(fare).currency("USD").status(ScheduleStatus.SCHEDULED).build();
    }

    @Test
    void findMatchingIds_sortsByDepartureTimeAscendingByDefault() {
        given(scheduleRepository.findAll(any(Specification.class), any(Sort.class))).willReturn(List.of());

        scheduleCatalogCache.findMatchingIds(
                new ScheduleSearchCriteria(null, null, null, null, null, null, null, null, null),
                Instant.parse("2026-01-01T00:00:00Z"));

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(scheduleRepository).findAll(any(Specification.class), sortCaptor.capture());

        Sort.Order order = sortCaptor.getValue().getOrderFor("departureTime");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void findMatchingIds_withSortByPriceAsc_sortsByBaseFareAscending() {
        given(scheduleRepository.findAll(any(Specification.class), any(Sort.class))).willReturn(List.of());

        scheduleCatalogCache.findMatchingIds(
                new ScheduleSearchCriteria(null, null, null, null, null, null, null, null, ScheduleSortBy.PRICE_ASC),
                Instant.parse("2026-01-01T00:00:00Z"));

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(scheduleRepository).findAll(any(Specification.class), sortCaptor.capture());

        Sort.Order order = sortCaptor.getValue().getOrderFor("baseFare");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void findMatchingIds_withSortByPriceDesc_sortsByBaseFareDescending() {
        given(scheduleRepository.findAll(any(Specification.class), any(Sort.class))).willReturn(List.of());

        scheduleCatalogCache.findMatchingIds(
                new ScheduleSearchCriteria(null, null, null, null, null, null, null, null, ScheduleSortBy.PRICE_DESC),
                Instant.parse("2026-01-01T00:00:00Z"));

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(scheduleRepository).findAll(any(Specification.class), sortCaptor.capture());

        Sort.Order order = sortCaptor.getValue().getOrderFor("baseFare");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void findMatchingIds_returnsScheduleIdsInRepositoryOrder() {
        Route route = route(10L, RouteType.BUS, "NYC", "Boston", null);
        Schedule scheduleA = schedule(5L, route, Instant.parse("2026-08-01T10:00:00Z"), BigDecimal.TEN);
        Schedule scheduleB = schedule(2L, route, Instant.parse("2026-08-02T10:00:00Z"), BigDecimal.TEN);
        given(scheduleRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(scheduleA, scheduleB));

        List<Long> ids = scheduleCatalogCache.findMatchingIds(
                new ScheduleSearchCriteria(null, null, null, null, null, null, null, null, null),
                Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(ids).containsExactly(5L, 2L);
    }

    @Test
    void findStaticInfo_whenScheduleExists_mapsRouteAndScheduleStaticFields() {
        Route route = route(10L, RouteType.BUS, "NYC", "Boston", null);
        Schedule schedule = schedule(1L, route, Instant.parse("2026-08-01T10:00:00Z"), new BigDecimal("25.00"));
        given(scheduleRepository.findById(1L)).willReturn(Optional.of(schedule));

        Optional<ScheduleStaticInfo> info = scheduleCatalogCache.findStaticInfo(1L);

        assertThat(info).isPresent();
        assertThat(info.get().scheduleId()).isEqualTo(1L);
        assertThat(info.get().routeId()).isEqualTo(10L);
        assertThat(info.get().origin()).isEqualTo("NYC");
        assertThat(info.get().destination()).isEqualTo("Boston");
        assertThat(info.get().baseFare()).isEqualByComparingTo("25.00");
    }

    @Test
    void findStaticInfo_whenScheduleMissing_returnsEmpty() {
        given(scheduleRepository.findById(99L)).willReturn(Optional.empty());

        assertThat(scheduleCatalogCache.findStaticInfo(99L)).isEmpty();
    }
}
