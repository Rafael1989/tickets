package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.dto.ScheduleSearchResult;
import com.ticketwave.catalog.dto.ScheduleStaticInfo;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.mapper.SeatMapper;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.ScheduleSeatCount;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.pricing.service.PricingService;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ScheduleSearchServiceImpl implements ScheduleSearchService {

    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final SeatMapper seatMapper;
    private final PricingService pricingService;
    private final ScheduleCatalogCache scheduleCatalogCache;
    private final Clock clock;

    public ScheduleSearchServiceImpl(
            ScheduleRepository scheduleRepository,
            SeatRepository seatRepository,
            UserRepository userRepository,
            SeatMapper seatMapper,
            PricingService pricingService,
            ScheduleCatalogCache scheduleCatalogCache,
            Clock clock
    ) {
        this.scheduleRepository = scheduleRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.seatMapper = seatMapper;
        this.pricingService = pricingService;
        this.scheduleCatalogCache = scheduleCatalogCache;
        this.clock = clock;
    }

    /**
     * The matching-schedule-id list and each schedule's static fields come
     * from ScheduleCatalogCache (short-TTL cached); availableSeats is always
     * read fresh here and never cached, since it's genuinely real-time —
     * changing on every seat hold/release/booking.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ScheduleSearchResult> search(ScheduleSearchCriteria criteria) {
        List<Long> scheduleIds = scheduleCatalogCache.findMatchingIds(criteria, clock.instant());
        if (scheduleIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> availableSeatsByScheduleId = seatRepository
                .countAvailableGroupedByScheduleId(scheduleIds, SeatStatus.AVAILABLE).stream()
                .collect(Collectors.toMap(ScheduleSeatCount::getScheduleId, ScheduleSeatCount::getAvailableCount));

        return scheduleIds.stream()
                .map(scheduleCatalogCache::findStaticInfo)
                .flatMap(Optional::stream)
                .map(info -> toSearchResult(info, availableSeatsByScheduleId.getOrDefault(info.scheduleId(), 0L)))
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
        ScheduleStaticInfo info = scheduleCatalogCache.findStaticInfo(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
        long availableSeats = seatRepository.countByScheduleIdAndStatus(scheduleId, SeatStatus.AVAILABLE);
        return toSearchResult(info, availableSeats);
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

    private ScheduleSearchResult toSearchResult(ScheduleStaticInfo info, long availableSeats) {
        return new ScheduleSearchResult(
                info.scheduleId(),
                info.routeId(),
                info.type(),
                info.origin(),
                info.destination(),
                info.venue(),
                info.departureTime(),
                info.arrivalTime(),
                info.baseFare(),
                info.currency(),
                info.status(),
                availableSeats
        );
    }
}
