package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.dto.ScheduleSearchResult;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.mapper.SeatMapper;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.ScheduleSeatCount;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.catalog.specification.ScheduleSpecifications;
import com.ticketwave.pricing.service.PricingService;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScheduleSearchServiceImpl implements ScheduleSearchService {

    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final SeatMapper seatMapper;
    private final PricingService pricingService;
    private final Clock clock;

    public ScheduleSearchServiceImpl(
            ScheduleRepository scheduleRepository,
            SeatRepository seatRepository,
            UserRepository userRepository,
            SeatMapper seatMapper,
            PricingService pricingService,
            Clock clock
    ) {
        this.scheduleRepository = scheduleRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.seatMapper = seatMapper;
        this.pricingService = pricingService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleSearchResult> search(ScheduleSearchCriteria criteria) {
        List<Schedule> schedules = scheduleRepository.findAll(
                ScheduleSpecifications.matching(criteria, clock.instant()),
                Sort.by(Sort.Direction.ASC, "departureTime"));

        if (schedules.isEmpty()) {
            return List.of();
        }

        List<Long> scheduleIds = schedules.stream().map(Schedule::getId).toList();
        Map<Long, Long> availableSeatsByScheduleId = seatRepository
                .countAvailableGroupedByScheduleId(scheduleIds, SeatStatus.AVAILABLE).stream()
                .collect(Collectors.toMap(ScheduleSeatCount::getScheduleId, ScheduleSeatCount::getAvailableCount));

        return schedules.stream()
                .map(schedule -> toSearchResult(schedule, availableSeatsByScheduleId.getOrDefault(schedule.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getAvailableSeatCount(Long scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new ScheduleNotFoundException(scheduleId);
        }
        return seatRepository.countByScheduleIdAndStatus(scheduleId, SeatStatus.AVAILABLE);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleSearchResult getScheduleDetails(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
        long availableSeats = seatRepository.countByScheduleIdAndStatus(scheduleId, SeatStatus.AVAILABLE);
        return toSearchResult(schedule, availableSeats);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatsForSchedule(Long scheduleId, String username) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));

        Long callerId = username == null
                ? null
                : userRepository.findByUsername(username).map(User::getId).orElse(null);

        return seatRepository.findByScheduleId(scheduleId).stream()
                .map(seat -> toSeatResponse(schedule, seat, callerId))
                .toList();
    }

    private SeatResponse toSeatResponse(Schedule schedule, Seat seat, Long callerId) {
        SeatResponse base = seatMapper.toResponse(seat);
        BigDecimal estimatedFare = pricingService.calculateSeatFare(schedule, seat);
        boolean heldByMe = callerId != null
                && seat.getHeldBy() != null
                && seat.getHeldBy().getId().equals(callerId);

        return new SeatResponse(
                base.id(),
                base.scheduleId(),
                base.seatNumber(),
                base.seatClass(),
                base.status(),
                base.priceModifier(),
                estimatedFare,
                base.heldUntil(),
                heldByMe
        );
    }

    private ScheduleSearchResult toSearchResult(Schedule schedule, long availableSeats) {
        Route route = schedule.getRoute();

        return new ScheduleSearchResult(
                schedule.getId(),
                route.getId(),
                route.getType(),
                route.getOrigin(),
                route.getDestination(),
                route.getVenue(),
                schedule.getDepartureTime(),
                schedule.getArrivalTime(),
                schedule.getBaseFare(),
                schedule.getCurrency(),
                schedule.getStatus(),
                availableSeats
        );
    }
}
