package com.ticketwave.catalog.repository;

import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.specification.ScheduleSpecifications;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserRole;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises ScheduleSpecifications against a real PostgreSQL instance, since
 * case-insensitive matching, UTC date-range filtering, and CANCELLED
 * exclusion are all DB-behavior-dependent (per project policy: integration
 * tests run against real Postgres, not H2). @DataJpaTest can't share
 * AbstractIntegrationTest (mutually exclusive bootstrap strategy from
 * @SpringBootTest), so it activates the "test" profile directly — same
 * application-test.yml, same ticketwave_test database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ScheduleSpecificationsIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void matching_filtersCaseInsensitivelyByPartialRouteMatchAndExcludesCancelled() {
        User operator = userRepository.save(operator("operator1"));

        Route nycToBoston = routeRepository.save(route(operator, RouteType.BUS, "NYC", "Boston"));
        Route laToSf = routeRepository.save(route(operator, RouteType.BUS, "LA", "San Francisco"));

        Schedule matching = scheduleRepository.save(
                schedule(nycToBoston, Instant.parse("2026-08-10T09:00:00Z"), ScheduleStatus.SCHEDULED));
        Schedule cancelled = scheduleRepository.save(
                schedule(nycToBoston, Instant.parse("2026-08-10T15:00:00Z"), ScheduleStatus.CANCELLED));
        Schedule otherRoute = scheduleRepository.save(
                schedule(laToSf, Instant.parse("2026-08-10T09:00:00Z"), ScheduleStatus.SCHEDULED));

        ScheduleSearchCriteria criteria =
                new ScheduleSearchCriteria(null, "yc", "osto", null, LocalDate.of(2026, 8, 10), null, null, null, null);

        List<Schedule> results = scheduleRepository.findAll(ScheduleSpecifications.matching(criteria, NOW));

        assertThat(results).extracting(Schedule::getId).containsExactly(matching.getId());
        assertThat(results).extracting(Schedule::getId).doesNotContain(cancelled.getId(), otherRoute.getId());
    }

    @Test
    void departsOn_excludesSchedulesOutsideTheUtcCalendarDay() {
        User operator = userRepository.save(operator("operator2"));
        Route route = routeRepository.save(route(operator, RouteType.TRAIN, "A", "B"));

        Schedule sameDay = scheduleRepository.save(
                schedule(route, Instant.parse("2026-08-10T23:59:00Z"), ScheduleStatus.SCHEDULED));
        Schedule nextDay = scheduleRepository.save(
                schedule(route, Instant.parse("2026-08-11T00:00:01Z"), ScheduleStatus.SCHEDULED));

        ScheduleSearchCriteria criteria =
                new ScheduleSearchCriteria(null, null, null, null, LocalDate.of(2026, 8, 10), null, null, null, null);

        List<Schedule> results = scheduleRepository.findAll(ScheduleSpecifications.matching(criteria, NOW));

        assertThat(results).extracting(Schedule::getId).containsExactly(sameDay.getId());
        assertThat(results).extracting(Schedule::getId).doesNotContain(nextDay.getId());
    }

    @Test
    void matching_excludesSchedulesThatHaveAlreadyDeparted() {
        // Deliberately scoped to "C"->"D" (an origin/destination pair no
        // other test/fixture uses) and asserts contains/doesNotContain
        // rather than containsExactly: an all-null criteria matches every
        // non-cancelled, non-departed schedule in the table, and this test
        // only owns two of them - it must not assert the table has nothing
        // else, since it can run alongside other IT classes against a
        // shared, non-Testcontainers-isolated database (see
        // ScheduleSpecificationsIT's own class Javadoc).
        User operator = userRepository.save(operator("operator3"));
        Route route = routeRepository.save(route(operator, RouteType.BUS, "C", "D"));

        Schedule departed = scheduleRepository.save(
                schedule(route, NOW.minusSeconds(3600), ScheduleStatus.SCHEDULED));
        Schedule upcoming = scheduleRepository.save(
                schedule(route, NOW.plusSeconds(3600), ScheduleStatus.SCHEDULED));

        ScheduleSearchCriteria criteria = new ScheduleSearchCriteria(null, null, null, null, null, null, null, null, null);

        List<Schedule> results = scheduleRepository.findAll(ScheduleSpecifications.matching(criteria, NOW));

        assertThat(results).extracting(Schedule::getId).contains(upcoming.getId());
        assertThat(results).extracting(Schedule::getId).doesNotContain(departed.getId());
    }

    private static User operator(String username) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hash")
                .role(UserRole.OPERATOR)
                .build();
    }

    private static Route route(User operator, RouteType type, String origin, String destination) {
        return Route.builder()
                .operator(operator)
                .type(type)
                .origin(origin)
                .destination(destination)
                .build();
    }

    private static Schedule schedule(Route route, Instant departureTime, ScheduleStatus status) {
        return Schedule.builder()
                .route(route)
                .departureTime(departureTime)
                .arrivalTime(departureTime.plusSeconds(3600))
                .baseFare(new BigDecimal("10.00"))
                .currency("USD")
                .status(status)
                .build();
    }
}
