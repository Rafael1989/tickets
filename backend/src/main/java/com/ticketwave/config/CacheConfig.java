package com.ticketwave.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * A short, fixed TTL on purpose: the cached data (schedule/route static
 * fields — see ScheduleCatalogCache) excludes anything genuinely real-time,
 * such as seat availability counts, which are never cached and always read
 * fresh. The TTL only bounds how quickly an operator's schedule/route edit
 * (see the @CacheEvict calls in ScheduleManagementServiceImpl and
 * RouteServiceImpl, which cover the common case) becomes visible to a caller
 * who doesn't trigger an eviction — e.g. a different route's edit landing in
 * the same "search results" cache entry.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("scheduleSearchIds", "scheduleStaticInfo");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(2_000));
        return cacheManager;
    }
}
