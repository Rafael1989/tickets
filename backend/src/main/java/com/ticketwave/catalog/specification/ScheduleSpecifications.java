package com.ticketwave.catalog.specification;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.entity.Seat;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class ScheduleSpecifications {

    private ScheduleSpecifications() {
    }

    public static Specification<Schedule> matching(ScheduleSearchCriteria criteria, Instant now) {
        return Specification.allOf(
                hasType(criteria.type()),
                hasOrigin(criteria.origin()),
                hasDestination(criteria.destination()),
                hasVenue(criteria.venue()),
                departsOn(criteria.departureDate()),
                hasMinPrice(criteria.minPrice()),
                hasMaxPrice(criteria.maxPrice()),
                hasSeatClass(criteria.seatClass()),
                isNotCancelled(),
                departsInFuture(now)
        );
    }

    public static Specification<Schedule> hasMinPrice(BigDecimal minPrice) {
        return (root, query, cb) -> minPrice == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("baseFare"), minPrice);
    }

    public static Specification<Schedule> hasMaxPrice(BigDecimal maxPrice) {
        return (root, query, cb) -> maxPrice == null
                ? null
                : cb.lessThanOrEqualTo(root.get("baseFare"), maxPrice);
    }

    /** Matches a schedule with at least one seat of the given class, in any status. */
    public static Specification<Schedule> hasSeatClass(String seatClass) {
        return (root, query, cb) -> {
            if (isBlank(seatClass)) {
                return null;
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            var seatRoot = subquery.from(Seat.class);
            subquery.select(seatRoot.get("id"))
                    .where(cb.and(
                            cb.equal(seatRoot.get("schedule"), root),
                            cb.equal(cb.lower(seatRoot.get("seatClass")), seatClass.trim().toLowerCase())));
            return cb.exists(subquery);
        };
    }

    public static Specification<Schedule> hasType(RouteType type) {
        return (root, query, cb) -> type == null
                ? null
                : cb.equal(root.get("route").get("type"), type);
    }

    public static Specification<Schedule> hasOrigin(String origin) {
        return (root, query, cb) -> isBlank(origin)
                ? null
                : cb.like(cb.lower(root.get("route").get("origin")), likePattern(origin), LIKE_ESCAPE);
    }

    public static Specification<Schedule> hasDestination(String destination) {
        return (root, query, cb) -> isBlank(destination)
                ? null
                : cb.like(cb.lower(root.get("route").get("destination")), likePattern(destination), LIKE_ESCAPE);
    }

    public static Specification<Schedule> hasVenue(String venue) {
        return (root, query, cb) -> isBlank(venue)
                ? null
                : cb.like(cb.lower(root.get("route").get("venue")), likePattern(venue), LIKE_ESCAPE);
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

    /**
     * Always applied: nothing in the app ever transitions a schedule's
     * status to COMPLETED, so departureTime is the only signal that a
     * schedule has already happened and shouldn't be a valid search result.
     */
    public static Specification<Schedule> departsInFuture(Instant now) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("departureTime"), now);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final char LIKE_ESCAPE = '\\';

    private static String likePattern(String value) {
        String escaped = value.trim().toLowerCase()
                .replace(String.valueOf(LIKE_ESCAPE), "" + LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }
}
