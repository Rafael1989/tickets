package com.ticketwave.catalog.repository;

import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

/**
 * Extends JpaSpecificationExecutor so the search/inventory service can compose
 * optional filters (origin, destination, date range, type, price) without the
 * repository growing a derived-query method per filter combination.
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Long>, JpaSpecificationExecutor<Schedule> {

    List<Schedule> findByRouteId(Long routeId);

    List<Schedule> findByStatus(ScheduleStatus status);

    List<Schedule> findByRouteIdAndDepartureTimeBetween(Long routeId, Instant from, Instant to);

    /**
     * Overrides JpaSpecificationExecutor's findAll to always fetch route in
     * the same query — every search result reads route.* (type, origin,
     * destination, venue), so leaving it lazy would fire one extra SELECT per
     * returned schedule.
     */
    @Override
    @EntityGraph(attributePaths = "route")
    List<Schedule> findAll(Specification<Schedule> spec, Sort sort);
}
