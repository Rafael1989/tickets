package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Uses a real Spring context (not @InjectMocks) specifically because a unit
 * test with @InjectMocks bypasses the AOP proxy @Cacheable relies on — it
 * could pass even if the cache were wired wrong or missing entirely.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {CacheConfig.class, ScheduleCatalogCache.class})
class ScheduleCatalogCacheCachingTest {

    @MockitoBean
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleCatalogCache scheduleCatalogCache;

    @Autowired
    private CacheManager cacheManager;

    /**
     * The Spring context (and its Caffeine caches) is reused across test
     * methods in this class by default — without this, an earlier test's
     * cached entry leaks into the next one, since both use overlapping
     * cache keys (e.g. scheduleId 1L).
     */
    @BeforeEach
    void clearCaches() {
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @Test
    void findStaticInfo_calledTwiceWithSameId_hitsRepositoryOnlyOnce() {
        Route route = Route.builder().id(10L).type(RouteType.BUS).origin("NYC").destination("Boston").build();
        Schedule schedule = Schedule.builder().id(1L).route(route)
                .departureTime(Instant.parse("2026-08-01T10:00:00Z"))
                .arrivalTime(Instant.parse("2026-08-01T11:00:00Z"))
                .baseFare(new BigDecimal("25.00")).currency("USD").status(ScheduleStatus.SCHEDULED).build();
        given(scheduleRepository.findById(1L)).willReturn(Optional.of(schedule));

        scheduleCatalogCache.findStaticInfo(1L);
        scheduleCatalogCache.findStaticInfo(1L);

        verify(scheduleRepository, times(1)).findById(1L);
    }

    @Test
    void findMatchingIds_calledTwiceWithSameCriteria_hitsRepositoryOnlyOnce() {
        given(scheduleRepository.findAll(any(Specification.class), any(Sort.class))).willReturn(List.of());
        ScheduleSearchCriteria criteria = new ScheduleSearchCriteria(null, "NYC", null, null, null, null, null, null, null);

        scheduleCatalogCache.findMatchingIds(criteria, Instant.parse("2026-01-01T00:00:00Z"));
        scheduleCatalogCache.findMatchingIds(criteria, Instant.parse("2026-01-01T00:00:05Z"));

        verify(scheduleRepository, times(1)).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void findStaticInfo_calledWithDifferentIds_hitsRepositoryForEach() {
        given(scheduleRepository.findById(anyLong())).willReturn(Optional.empty());

        scheduleCatalogCache.findStaticInfo(1L);
        scheduleCatalogCache.findStaticInfo(2L);

        verify(scheduleRepository, times(1)).findById(1L);
        verify(scheduleRepository, times(1)).findById(2L);
    }
}
