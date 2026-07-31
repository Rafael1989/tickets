package com.ticketwave.catalog.specification;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class ScheduleSpecifications {

    private ScheduleSpecifications() {
    }

    public static Specification<Schedule> matching(ScheduleSearchCriteria criteria) {
        return Specification.allOf(
                hasType(criteria.type()),
                hasOrigin(criteria.origin()),
                hasDestination(criteria.destination()),
                hasVenue(criteria.venue()),
                departsOn(criteria.departureDate()),
                isNotCancelled()
        );
    }

    public static Specification<Schedule> hasType(RouteType type) {
        return (root, query, cb) -> type == null
                ? null
                : cb.equal(root.get("route").get("type"), type);
    }

    public static Specification<Schedule> hasOrigin(String origin) {
        return (root, query, cb) -> isBlank(origin)
                ? null
                : cb.equal(cb.lower(root.get("route").get("origin")), origin.toLowerCase());
    }

    public static Specification<Schedule> hasDestination(String destination) {
        return (root, query, cb) -> isBlank(destination)
                ? null
                : cb.equal(cb.lower(root.get("route").get("destination")), destination.toLowerCase());
    }

    public static Specification<Schedule> hasVenue(String venue) {
        return (root, query, cb) -> isBlank(venue)
                ? null
                : cb.equal(cb.lower(root.get("route").get("venue")), venue.toLowerCase());
    }

    /**
     * departureDate is interpreted as a UTC calendar day, matching schedules
     * whose departureTime falls within [00:00, 24:00) that day.
     */
    public static Specification<Schedule> departsOn(LocalDate departureDate) {
        return (root, query, cb) -> {
            if (departureDate == null) {
                return null;
            }
            Instant startOfDay = departureDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant startOfNextDay = departureDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            return cb.and(
                    cb.greaterThanOrEqualTo(root.get("departureTime"), startOfDay),
                    cb.lessThan(root.get("departureTime"), startOfNextDay)
            );
        };
    }

    /**
     * Always applied: a cancelled schedule is never a valid search result,
     * regardless of what the caller filters on.
     */
    public static Specification<Schedule> isNotCancelled() {
        return (root, query, cb) -> cb.notEqual(root.get("status"), ScheduleStatus.CANCELLED);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
