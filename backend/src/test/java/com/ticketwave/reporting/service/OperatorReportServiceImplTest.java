package com.ticketwave.reporting.service;

import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.repository.RouteBookingStats;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.catalog.repository.RouteSeatStats;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.reporting.dto.OperatorReportResponse;
import com.ticketwave.reporting.dto.RouteReportItem;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OperatorReportServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RouteRepository routeRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private OperatorReportServiceImpl reportService;

    private record BookingStats(Long routeId, long bookingCount, BigDecimal revenue) implements RouteBookingStats {
        @Override
        public Long getRouteId() {
            return routeId;
        }

        @Override
        public long getBookingCount() {
            return bookingCount;
        }

        @Override
        public BigDecimal getRevenue() {
            return revenue;
        }
    }

    private record SeatStats(Long routeId, long totalSeats, long bookedSeats) implements RouteSeatStats {
        @Override
        public Long getRouteId() {
            return routeId;
        }

        @Override
        public long getTotalSeats() {
            return totalSeats;
        }

        @Override
        public long getBookedSeats() {
            return bookedSeats;
        }
    }

    @Test
    void getReport_whenOperatorHasNoRoutes_returnsEmptyReportWithoutQueryingStats() {
        User operator = User.builder().id(1L).username("operator1").build();
        given(userRepository.findByUsername("operator1")).willReturn(Optional.of(operator));
        given(routeRepository.findByOperatorId(1L)).willReturn(List.of());

        OperatorReportResponse report = reportService.getReport("operator1");

        assertThat(report.routes()).isEmpty();
        assertThat(report.totalConfirmedBookings()).isZero();
        assertThat(report.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getReport_combinesBookingAndSeatStatsPerRouteAndSumsTotals() {
        User operator = User.builder().id(1L).username("operator1").build();
        Route route1 = Route.builder().id(10L).type(RouteType.BUS).origin("NYC").destination("Boston").build();
        Route route2 = Route.builder().id(20L).type(RouteType.EVENT).venue("Arena").build();

        given(userRepository.findByUsername("operator1")).willReturn(Optional.of(operator));
        given(routeRepository.findByOperatorId(1L)).willReturn(List.of(route1, route2));
        given(bookingRepository.aggregateConfirmedBookingsByRouteId(List.of(10L, 20L)))
                .willReturn(List.of(new BookingStats(10L, 5, new BigDecimal("500.00"))));
        given(seatRepository.aggregateSeatsByRouteId(List.of(10L, 20L)))
                .willReturn(List.of(new SeatStats(10L, 20, 15), new SeatStats(20L, 100, 0)));

        OperatorReportResponse report = reportService.getReport("operator1");

        assertThat(report.routes()).hasSize(2);
        RouteReportItem route1Report = report.routes().stream().filter(r -> r.routeId().equals(10L)).findFirst().orElseThrow();
        assertThat(route1Report.confirmedBookings()).isEqualTo(5);
        assertThat(route1Report.revenue()).isEqualByComparingTo("500.00");
        assertThat(route1Report.occupancyRate()).isEqualByComparingTo("0.7500");

        RouteReportItem route2Report = report.routes().stream().filter(r -> r.routeId().equals(20L)).findFirst().orElseThrow();
        assertThat(route2Report.confirmedBookings()).isZero();
        assertThat(route2Report.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(route2Report.occupancyRate()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(report.totalConfirmedBookings()).isEqualTo(5);
        assertThat(report.totalRevenue()).isEqualByComparingTo("500.00");
    }

    @Test
    void getReport_whenOperatorBelongsToAPartner_usesThePartnersRoutes() {
        Partner partner = Partner.builder().id(9L).build();
        User operator = User.builder().id(1L).username("operator1").partner(partner).build();
        given(userRepository.findByUsername("operator1")).willReturn(Optional.of(operator));
        given(routeRepository.findByOperatorPartnerId(9L)).willReturn(List.of());

        reportService.getReport("operator1");

        org.mockito.Mockito.verify(routeRepository, org.mockito.Mockito.never()).findByOperatorId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getReport_whenOperatorMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getReport("ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }
}
