package com.ticketwave.reporting.service;

import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.repository.RouteBookingStats;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.catalog.repository.RouteSeatStats;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.reporting.dto.OperatorReportResponse;
import com.ticketwave.reporting.dto.RouteReportItem;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OperatorReportServiceImpl implements OperatorReportService {

    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;

    public OperatorReportServiceImpl(
            UserRepository userRepository,
            RouteRepository routeRepository,
            BookingRepository bookingRepository,
            SeatRepository seatRepository
    ) {
        this.userRepository = userRepository;
        this.routeRepository = routeRepository;
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional(readOnly = true)
    public OperatorReportResponse getReport(String operatorUsername) {
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));

        List<Route> routes = operator.getPartner() != null
                ? routeRepository.findByOperatorPartnerId(operator.getPartner().getId())
                : routeRepository.findByOperatorId(operator.getId());

        if (routes.isEmpty()) {
            return new OperatorReportResponse(List.of(), 0, BigDecimal.ZERO);
        }

        List<Long> routeIds = routes.stream().map(Route::getId).toList();
        Map<Long, RouteBookingStats> bookingStatsByRoute = bookingRepository.aggregateConfirmedBookingsByRouteId(routeIds).stream()
                .collect(Collectors.toMap(RouteBookingStats::getRouteId, Function.identity()));
        Map<Long, RouteSeatStats> seatStatsByRoute = seatRepository.aggregateSeatsByRouteId(routeIds).stream()
                .collect(Collectors.toMap(RouteSeatStats::getRouteId, Function.identity()));

        List<RouteReportItem> items = routes.stream()
                .map(route -> toReportItem(route, bookingStatsByRoute.get(route.getId()), seatStatsByRoute.get(route.getId())))
                .toList();

        long totalConfirmedBookings = items.stream().mapToLong(RouteReportItem::confirmedBookings).sum();
        BigDecimal totalRevenue = items.stream().map(RouteReportItem::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OperatorReportResponse(items, totalConfirmedBookings, totalRevenue);
    }

    private RouteReportItem toReportItem(Route route, RouteBookingStats bookingStats, RouteSeatStats seatStats) {
        long confirmedBookings = bookingStats != null ? bookingStats.getBookingCount() : 0;
        BigDecimal revenue = bookingStats != null ? bookingStats.getRevenue() : BigDecimal.ZERO;
        long totalSeats = seatStats != null ? seatStats.getTotalSeats() : 0;
        long bookedSeats = seatStats != null ? seatStats.getBookedSeats() : 0;
        BigDecimal occupancyRate = totalSeats == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(bookedSeats).divide(BigDecimal.valueOf(totalSeats), 4, RoundingMode.HALF_UP);

        return new RouteReportItem(route.getId(), route.getType(), route.getOrigin(), route.getDestination(),
                route.getVenue(), confirmedBookings, revenue, totalSeats, bookedSeats, occupancyRate);
    }
}
