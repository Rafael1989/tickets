package com.ticketwave.catalog.specification;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class ScheduleSpecificationsTest {

    @Mock
    private Root<Schedule> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path routePath;
    @Mock
    private Path fieldPath;
    @Mock
    private Predicate predicate;
    @Mock
    private Predicate predicate2;

    @Test
    void hasType_withNull_returnsNoPredicate() {
        assertThat(ScheduleSpecifications.hasType(null).toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasType_withValue_buildsEqualityPredicateOnRouteType() {
        given(root.get("route")).willReturn(routePath);
        given(routePath.get("type")).willReturn(fieldPath);
        given(cb.equal(fieldPath, RouteType.BUS)).willReturn(predicate);

        Predicate result = ScheduleSpecifications.hasType(RouteType.BUS).toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void hasOrigin_withNull_returnsNoPredicate() {
        assertThat(ScheduleSpecifications.hasOrigin(null).toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasOrigin_withBlank_returnsNoPredicate() {
        assertThat(ScheduleSpecifications.hasOrigin("   ").toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasOrigin_withValue_buildsCaseInsensitivePartialMatchPredicate() {
        given(root.get("route")).willReturn(routePath);
        given(routePath.get("origin")).willReturn(fieldPath);
        given(cb.lower(fieldPath)).willReturn(fieldPath);
        given(cb.like(fieldPath, "%boston%", '\\')).willReturn(predicate);

        Predicate result = ScheduleSpecifications.hasOrigin("Boston").toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void hasOrigin_withLikeWildcardsInValue_escapesThemInThePattern() {
        given(root.get("route")).willReturn(routePath);
        given(routePath.get("origin")).willReturn(fieldPath);
        given(cb.lower(fieldPath)).willReturn(fieldPath);
        given(cb.like(fieldPath, "%50\\%\\_off%", '\\')).willReturn(predicate);

        Predicate result = ScheduleSpecifications.hasOrigin("50%_off").toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void hasDestination_withNull_returnsNoPredicate() {
        assertThat(ScheduleSpecifications.hasDestination(null).toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasDestination_withBlank_returnsNoPredicate() {
        assertThat(ScheduleSpecifications.hasDestination("").toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasDestination_withValue_buildsCaseInsensitivePartialMatchPredicate() {
        given(root.get("route")).willReturn(routePath);
        given(routePath.get("destination")).willReturn(fieldPath);
        given(cb.lower(fieldPath)).willReturn(fieldPath);
        given(cb.like(fieldPath, "%chicago%", '\\')).willReturn(predicate);

        Predicate result = ScheduleSpecifications.hasDestination("Chicago").toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void hasVenue_withNull_returnsNoPredicate() {
        assertThat(ScheduleSpecifications.hasVenue(null).toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasVenue_withBlank_returnsNoPredicate() {
        assertThat(ScheduleSpecifications.hasVenue("  ").toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasVenue_withValue_buildsCaseInsensitivePartialMatchPredicate() {
        given(root.get("route")).willReturn(routePath);
        given(routePath.get("venue")).willReturn(fieldPath);
        given(cb.lower(fieldPath)).willReturn(fieldPath);
        given(cb.like(fieldPath, "%arena%", '\\')).willReturn(predicate);

        Predicate result = ScheduleSpecifications.hasVenue("Arena").toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void departsOn_withNull_returnsNoPredicate() {
        assertThat(ScheduleSpecifications.departsOn(null).toPredicate(root, query, cb)).isNull();
    }

    @Test
    void departsOn_withDate_buildsRangePredicateForTheUtcCalendarDay() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        Instant startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextDay = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        given(root.get("departureTime")).willReturn(fieldPath);
        given(cb.greaterThanOrEqualTo(fieldPath, startOfDay)).willReturn(predicate);
        given(cb.lessThan(fieldPath, startOfNextDay)).willReturn(predicate2);
        given(cb.and(predicate, predicate2)).willReturn(predicate);

        Predicate result = ScheduleSpecifications.departsOn(date).toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void isNotCancelled_buildsNotEqualPredicateAgainstCancelledStatus() {
        given(root.get("status")).willReturn(fieldPath);
        given(cb.notEqual(fieldPath, ScheduleStatus.CANCELLED)).willReturn(predicate);

        Predicate result = ScheduleSpecifications.isNotCancelled().toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void departsInFuture_buildsGreaterThanOrEqualPredicateAgainstNow() {
        Instant now = Instant.parse("2026-01-15T00:00:00Z");
        given(root.get("departureTime")).willReturn(fieldPath);
        given(cb.greaterThanOrEqualTo(fieldPath, now)).willReturn(predicate);

        Predicate result = ScheduleSpecifications.departsInFuture(now).toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void matching_combinesAllCriteriaIntoASingleSpecification() {
        ScheduleSearchCriteria criteria = new ScheduleSearchCriteria(
                RouteType.BUS, "Boston", "Chicago", "Arena", LocalDate.of(2026, 1, 15));

        Specification<Schedule> specification = ScheduleSpecifications.matching(criteria, Instant.now());

        assertThat(specification).isNotNull();
    }

    @Test
    void matching_withAllNullCriteria_stillReturnsASpecification() {
        ScheduleSearchCriteria criteria = new ScheduleSearchCriteria(null, null, null, null, null);

        Specification<Schedule> specification = ScheduleSpecifications.matching(criteria, Instant.now());

        assertThat(specification).isNotNull();
    }
}
