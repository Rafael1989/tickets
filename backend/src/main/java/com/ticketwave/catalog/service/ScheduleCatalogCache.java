package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.dto.ScheduleSortBy;
import com.ticketwave.catalog.dto.ScheduleStaticInfo;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.specification.ScheduleSpecifications;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The cached read path for schedule/route search — see CacheConfig for why
 * the TTL is short and why availableSeats never appears here: that field is
 * genuinely real-time (changes on every seat hold/release/booking) and is
 * always read fresh by ScheduleSearchServiceImpl, joined onto whatever this
 * class returns.
 *
 * A separate bean (not methods on ScheduleSearchServiceImpl) because
 * Spring's @Cacheable only intercepts calls that go through the bean's
 * proxy — a method calling another @Cacheable method on `this` bypasses the
 * proxy and never actually caches.
 */
@Service
public class ScheduleCatalogCache {

    private final ScheduleRepository scheduleRepository;

    public ScheduleCatalogCache(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * now is deliberately excluded from the cache key (see the SpEL key
     * below): including it would make every call a unique key and defeat
     * the cache entirely, since Clock.instant() differs on every call. The
     * accepted tradeoff is that a schedule crossing from "not yet departed"
     * to "departed" can still appear in a cached result for up to the
     * cache's TTL.
     */
    @Cacheable(cacheNames = "scheduleSearchIds", key = "#criteria")
    @Transactional(readOnly = true)
    public List<Long> findMatchingIds(ScheduleSearchCriteria criteria, Instant now) {
        return scheduleRepository.findAll(ScheduleSpecifications.matching(criteria, now), sortFor(criteria.sortBy()))
                .stream()
                .map(Schedule::getId)
                .toList();
    }

    @Cacheable(cacheNames = "scheduleStaticInfo", key = "#scheduleId")
    @Transactional(readOnly = true)
    public Optional<ScheduleStaticInfo> findStaticInfo(Long scheduleId) {
        return scheduleRepository.findById(scheduleId).map(ScheduleCatalogCache::toStaticInfo);
    }

    /** Defaults to soonest-departing-first when sortBy is omitted. */
    private static Sort sortFor(ScheduleSortBy sortBy) {
        if (sortBy == null) {
            return Sort.by(Sort.Direction.ASC, "departureTime");
        }
        return switch (sortBy) {
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "baseFare");
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "baseFare");
            case DEPARTURE_TIME -> Sort.by(Sort.Direction.ASC, "departureTime");
        };
    }

    private static ScheduleStaticInfo toStaticInfo(Schedule schedule) {
        Route route = schedule.getRoute();
        return new ScheduleStaticInfo(
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
                schedule.getStatus()
        );
    }
}
