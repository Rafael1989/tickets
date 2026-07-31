package com.ticketwave.devseed;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.SeatSelection;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.config.RefundProperties;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.RefundDecision;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.service.PaymentService;
import com.ticketwave.payment.service.RefundService;
import com.ticketwave.user.dto.PassengerRequest;
import com.ticketwave.user.dto.PassengerResponse;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserRole;
import com.ticketwave.user.repository.UserRepository;
import com.ticketwave.user.service.PassengerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Populates a fresh dev database with a realistic, heavily-populated dataset:
 * users for every role, a catalog of routes/schedules/seats across bus,
 * train, flight and event, and a spread of bookings in every lifecycle state
 * (initiated, confirmed, cancelled — with and without a refund attached).
 *
 * Only runs under the "seed" Spring profile (never in a normal boot), and
 * only against a database that doesn't already look seeded (checks for
 * admin1), so it's safe to leave the profile on across repeated restarts.
 *
 * Deliberately goes through the real BookingService/PaymentService/
 * RefundService/PassengerService rather than constructing Booking/Payment/
 * Refund rows directly — that's the only way the seeded data ends up
 * internally consistent with this app's own business rules (PNR generation,
 * dynamic pricing, seat-hold lifecycle, refund proration). Those services
 * run outside any HTTP request here, so calls into the @PreAuthorize-guarded
 * ones go through SeedAuthContext to stand up a temporary Authentication.
 *
 * Run with: mvn spring-boot:run -Dspring-boot.run.profiles=seed
 * (plus JWT_SECRET, as always). Every seeded account shares one password —
 * see SEED_PASSWORD below, also logged at the end of the run.
 */
@Component
@Profile("seed")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private static final String SEED_PASSWORD = "SeedPass123!";

    private static final List<String> DOMESTIC_CITIES = List.of(
            "New York", "Los Angeles", "Chicago", "Houston", "Phoenix",
            "Philadelphia", "San Antonio", "San Diego", "Dallas", "Miami");

    private static final List<String> INTERNATIONAL_CITIES = List.of(
            "London", "Paris", "Tokyo", "Dubai", "Sydney", "Toronto");

    private static final List<String> EVENT_VENUES = List.of(
            "Madison Square Garden", "The O2 Arena", "Sydney Opera House", "Tokyo Dome");

    private static final List<Integer> DEPARTURE_HOURS = List.of(6, 8, 10, 13, 16, 19);

    private static final List<String> FIRST_NAMES = List.of(
            "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda",
            "David", "Elizabeth", "Wei", "Priya", "Ahmed", "Sofia", "Liam", "Olivia");

    private static final List<String> LAST_NAMES = List.of(
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
            "Rodriguez", "Martinez", "Chen", "Patel", "Khan", "Silva", "Kim", "Nguyen");

    private static final List<String> ID_TYPES = List.of("passport", "national_id", "driver_license");

    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final RefundProperties refundProperties;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final PassengerService passengerService;

    public DevDataSeeder(
            UserRepository userRepository,
            RouteRepository routeRepository,
            ScheduleRepository scheduleRepository,
            SeatRepository seatRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            RefundProperties refundProperties,
            BookingService bookingService,
            PaymentService paymentService,
            RefundService refundService,
            PassengerService passengerService
    ) {
        this.userRepository = userRepository;
        this.routeRepository = routeRepository;
        this.scheduleRepository = scheduleRepository;
        this.seatRepository = seatRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.refundProperties = refundProperties;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.passengerService = passengerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername("admin1")) {
            log.info("Dev seed data already present (found admin1) — skipping.");
            return;
        }

        Instant startedAt = clock.instant();
        log.info("Seeding dev data...");

        String seedPasswordHash = passwordEncoder.encode(SEED_PASSWORD);
        List<User> admins = seedUsersForRole(UserRole.ADMIN, "admin", 2, seedPasswordHash);
        List<User> support = seedUsersForRole(UserRole.SUPPORT, "support", 3, seedPasswordHash);
        List<User> operators = seedUsersForRole(UserRole.OPERATOR, "operator", 6, seedPasswordHash);
        List<User> customers = seedUsersForRole(UserRole.CUSTOMER, "customer", 30, seedPasswordHash);
        log.info("Seeded {} users ({} admin, {} support, {} operator, {} customer).",
                admins.size() + support.size() + operators.size() + customers.size(),
                admins.size(), support.size(), operators.size(), customers.size());

        Map<String, List<PassengerResponse>> passengersByUsername = seedPassengers(customers);
        log.info("Seeded passengers for {} customers.", passengersByUsername.size());

        CatalogPools pools = seedCatalog(operators);
        int scheduleCount = pools.generalPool().size() + pools.midWindowPool().size() + pools.farFlightPool().size();
        int seatCount = pools.seatPoolByScheduleId().values().stream().mapToInt(Deque::size).sum();
        log.info("Seeded {} routes across bus/train/flight/event, {} schedules, {} seats.",
                routeRepository.count(), scheduleCount, seatCount);

        seedBookings(pools, customers, passengersByUsername, support);
        log.info("Seeded demo bookings, payments, and refunds across every lifecycle state.");

        Duration elapsed = Duration.between(startedAt, clock.instant());
        log.info("Dev data seed complete in {}s.", elapsed.toSeconds());
        log.info("All seeded accounts share one password: '{}'. Usernames: admin1-2, support1-3, operator1-6, customer1-30.",
                SEED_PASSWORD);
    }

    // ------------------------------------------------------------------
    // Users & passengers
    // ------------------------------------------------------------------

    private List<User> seedUsersForRole(UserRole role, String usernamePrefix, int count, String passwordHash) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String username = usernamePrefix + i;
            users.add(userRepository.save(User.builder()
                    .username(username)
                    .email(username + "@ticketwave.test")
                    .passwordHash(passwordHash)
                    .role(role)
                    .build()));
        }
        return users;
    }

    private Map<String, List<PassengerResponse>> seedPassengers(List<User> customers) {
        Map<String, List<PassengerResponse>> byUsername = new HashMap<>();
        Random random = new Random(42);
        int idCounter = 1;

        for (User customer : customers) {
            int passengerCount = 1 + random.nextInt(2);
            List<PassengerResponse> passengers = new ArrayList<>();
            for (int i = 0; i < passengerCount; i++) {
                String fullName = FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size()))
                        + " " + LAST_NAMES.get(random.nextInt(LAST_NAMES.size()));
                LocalDate dob = LocalDate.of(1960 + random.nextInt(45), 1 + random.nextInt(12), 1 + random.nextInt(28));
                String idType = ID_TYPES.get(random.nextInt(ID_TYPES.size()));
                String idNumber = "SEED" + String.format("%06d", idCounter++);

                passengers.add(passengerService.createPassenger(
                        customer.getUsername(),
                        new PassengerRequest(fullName, dob, idType, idNumber)));
            }
            byUsername.put(customer.getUsername(), passengers);
        }
        return byUsername;
    }

    // ------------------------------------------------------------------
    // Routes, schedules, seats
    // ------------------------------------------------------------------

    private record CityPair(String origin, String destination) {
    }

    private record CatalogPools(
            List<Schedule> generalPool,
            List<Schedule> midWindowPool,
            List<Schedule> farFlightPool,
            Map<Long, Deque<Seat>> seatPoolByScheduleId
    ) {
    }

    private CatalogPools seedCatalog(List<User> operators) {
        Random random = new Random(7);
        List<Schedule> generalPool = new ArrayList<>();
        List<Schedule> midWindowPool = new ArrayList<>();
        List<Schedule> farFlightPool = new ArrayList<>();
        Map<Long, Deque<Seat>> seatPoolByScheduleId = new HashMap<>();
        int[] operatorCursor = {0};

        // A day-offset far enough out to sit inside the partial-refund window
        // (more than partialRefundThresholdHours away, safely under
        // fullRefundThresholdDays) regardless of what those are configured to.
        int reservedDayOffset = Math.max(2, (int) (refundProperties.partialRefundThresholdHours() / 24) + 1);
        // A week-offset far enough out that departure is comfortably beyond
        // fullRefundThresholdDays.
        int farWeekThreshold = (int) Math.ceil((refundProperties.fullRefundThresholdDays() + 1) / 7.0);

        for (CityPair pair : bothDirections(cyclicPairs(DOMESTIC_CITIES, 1, 8))) {
            User operator = nextOperator(operators, operatorCursor);
            int duration = 180 + random.nextInt(121);
            Route route = createRoute(operator, RouteType.BUS, pair.origin(), pair.destination(), null, duration);
            generateDailySchedules(route, randomFare(random, 15, 45), duration, random,
                    generalPool, midWindowPool, seatPoolByScheduleId, reservedDayOffset);
        }

        for (CityPair pair : bothDirections(cyclicPairs(DOMESTIC_CITIES, 2, 6))) {
            User operator = nextOperator(operators, operatorCursor);
            int duration = 120 + random.nextInt(121);
            Route route = createRoute(operator, RouteType.TRAIN, pair.origin(), pair.destination(), null, duration);
            generateDailySchedules(route, randomFare(random, 25, 65), duration, random,
                    generalPool, midWindowPool, seatPoolByScheduleId, reservedDayOffset);
        }

        for (CityPair pair : bothDirections(cyclicPairs(DOMESTIC_CITIES, 3, 6))) {
            User operator = nextOperator(operators, operatorCursor);
            int duration = 90 + random.nextInt(151);
            Route route = createRoute(operator, RouteType.FLIGHT, pair.origin(), pair.destination(), null, duration);
            generateWeeklySchedules(route, randomFare(random, 80, 260), duration, random,
                    generalPool, farFlightPool, seatPoolByScheduleId, farWeekThreshold);
        }

        for (CityPair pair : bothDirections(domesticToInternationalPairs())) {
            User operator = nextOperator(operators, operatorCursor);
            int duration = 480 + random.nextInt(301);
            Route route = createRoute(operator, RouteType.FLIGHT, pair.origin(), pair.destination(), null, duration);
            generateWeeklySchedules(route, randomFare(random, 400, 1200), duration, random,
                    generalPool, farFlightPool, seatPoolByScheduleId, farWeekThreshold);
        }

        int eventDayOffset = 20;
        for (String venue : EVENT_VENUES) {
            User operator = nextOperator(operators, operatorCursor);
            Route route = createRoute(operator, RouteType.EVENT, null, null, venue, 150);
            Instant departure = clock.instant().plus(Duration.ofDays(eventDayOffset)).plus(Duration.ofHours(19));
            Schedule schedule = createSchedule(route, departure, 150, randomFare(random, 40, 250));
            trackSeats(schedule, seatPoolByScheduleId);
            generalPool.add(schedule);
            eventDayOffset += 5;
        }

        return new CatalogPools(generalPool, midWindowPool, farFlightPool, seatPoolByScheduleId);
    }

    private User nextOperator(List<User> operators, int[] cursor) {
        return operators.get(cursor[0]++ % operators.size());
    }

    private List<CityPair> bothDirections(List<CityPair> pairs) {
        List<CityPair> withReverse = new ArrayList<>();
        for (CityPair pair : pairs) {
            withReverse.add(pair);
            withReverse.add(new CityPair(pair.destination(), pair.origin()));
        }
        return withReverse;
    }

    private List<CityPair> cyclicPairs(List<String> cities, int step, int count) {
        List<CityPair> pairs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pairs.add(new CityPair(cities.get(i % cities.size()), cities.get((i + step) % cities.size())));
        }
        return pairs;
    }

    private List<CityPair> domesticToInternationalPairs() {
        List<CityPair> pairs = new ArrayList<>();
        for (int i = 0; i < INTERNATIONAL_CITIES.size(); i++) {
            pairs.add(new CityPair(DOMESTIC_CITIES.get(i), INTERNATIONAL_CITIES.get(i)));
        }
        return pairs;
    }

    private Route createRoute(User operator, RouteType type, String origin, String destination, String venue, int durationMinutes) {
        return routeRepository.save(Route.builder()
                .operator(operator)
                .type(type)
                .origin(origin)
                .destination(destination)
                .venue(venue)
                .durationMinutes(durationMinutes)
                .build());
    }

    private void generateDailySchedules(
            Route route, BigDecimal baseFare, int durationMinutes, Random random,
            List<Schedule> generalPool, List<Schedule> midWindowPool,
            Map<Long, Deque<Seat>> seatPoolByScheduleId, int reservedDayOffset
    ) {
        int hour = DEPARTURE_HOURS.get(random.nextInt(DEPARTURE_HOURS.size()));
        for (int dayOffset = 1; dayOffset <= 7; dayOffset++) {
            Instant departure = clock.instant().plus(Duration.ofDays(dayOffset)).plus(Duration.ofHours(hour));
            Schedule schedule = createSchedule(route, departure, durationMinutes, baseFare);
            trackSeats(schedule, seatPoolByScheduleId);
            (dayOffset == reservedDayOffset ? midWindowPool : generalPool).add(schedule);
        }
    }

    private void generateWeeklySchedules(
            Route route, BigDecimal baseFare, int durationMinutes, Random random,
            List<Schedule> generalPool, List<Schedule> farFlightPool,
            Map<Long, Deque<Seat>> seatPoolByScheduleId, int farWeekThreshold
    ) {
        int hour = DEPARTURE_HOURS.get(random.nextInt(DEPARTURE_HOURS.size()));
        for (int week = 0; week <= 3; week++) {
            Instant departure = clock.instant().plus(Duration.ofDays(week * 7L + 1)).plus(Duration.ofHours(hour));
            Schedule schedule = createSchedule(route, departure, durationMinutes, baseFare);
            trackSeats(schedule, seatPoolByScheduleId);
            (week >= farWeekThreshold ? farFlightPool : generalPool).add(schedule);
        }
    }

    private Schedule createSchedule(Route route, Instant departure, int durationMinutes, BigDecimal baseFare) {
        return scheduleRepository.save(Schedule.builder()
                .route(route)
                .departureTime(departure)
                .arrivalTime(departure.plus(Duration.ofMinutes(durationMinutes)))
                .baseFare(baseFare)
                .currency("USD")
                .status(ScheduleStatus.SCHEDULED)
                .build());
    }

    private void trackSeats(Schedule schedule, Map<Long, Deque<Seat>> seatPoolByScheduleId) {
        List<Seat> seats = seatRepository.saveAll(buildSeats(schedule, schedule.getRoute().getType()));
        seatPoolByScheduleId.put(schedule.getId(), new ArrayDeque<>(seats));
    }

    private BigDecimal randomFare(Random random, int min, int max) {
        return BigDecimal.valueOf(min + random.nextInt(max - min + 1)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Window/aisle isn't a separate column in the schema — like real
     * booking systems, it's implied by the seat letter's position in the
     * row (e.g. A/F on a 6-across economy row are window seats), which is
     * why priceModifier varies by column, not just by class.
     */
    private List<Seat> buildSeats(Schedule schedule, RouteType type) {
        List<Seat> seats = new ArrayList<>();
        switch (type) {
            case BUS -> addRows(seats, schedule, 1, 10, "economy",
                    "ABCD", "AD", new BigDecimal("1.000"), new BigDecimal("1.050"));
            case TRAIN -> {
                addRows(seats, schedule, 1, 2, "business",
                        "ABC", "AC", new BigDecimal("1.750"), new BigDecimal("1.850"));
                addRows(seats, schedule, 3, 14, "economy",
                        "ABCD", "AD", new BigDecimal("1.000"), new BigDecimal("1.050"));
            }
            case FLIGHT -> {
                addRows(seats, schedule, 1, 3, "business",
                        "ACDF", "AF", new BigDecimal("1.800"), new BigDecimal("1.900"));
                addRows(seats, schedule, 4, 16, "economy",
                        "ABCDEF", "AF", new BigDecimal("1.000"), new BigDecimal("1.100"));
            }
            case EVENT -> {
                for (int i = 1; i <= 30; i++) {
                    seats.add(newSeat(schedule, "VIP-" + i, "vip", new BigDecimal("2.500")));
                }
                for (int i = 1; i <= 120; i++) {
                    seats.add(newSeat(schedule, "GA-" + i, "general", BigDecimal.ONE));
                }
            }
        }
        return seats;
    }

    private void addRows(
            List<Seat> seats, Schedule schedule, int fromRow, int toRow, String seatClass,
            String columns, String windowColumns, BigDecimal baseModifier, BigDecimal windowModifier
    ) {
        for (int row = fromRow; row <= toRow; row++) {
            for (char column : columns.toCharArray()) {
                BigDecimal modifier = windowColumns.indexOf(column) >= 0 ? windowModifier : baseModifier;
                seats.add(newSeat(schedule, row + String.valueOf(column), seatClass, modifier));
            }
        }
    }

    private Seat newSeat(Schedule schedule, String seatNumber, String seatClass, BigDecimal priceModifier) {
        return Seat.builder()
                .schedule(schedule)
                .seatNumber(seatNumber)
                .seatClass(seatClass)
                .status(SeatStatus.AVAILABLE)
                .priceModifier(priceModifier)
                .build();
    }

    // ------------------------------------------------------------------
    // Bookings, payments, refunds
    // ------------------------------------------------------------------

    private void seedBookings(
            CatalogPools pools, List<User> customers,
            Map<String, List<PassengerResponse>> passengersByUsername, List<User> support
    ) {
        int[] customerCursor = {0};

        for (int i = 0; i < 12; i++) {
            createDemoBooking(nextCustomer(customers, customerCursor), passengersByUsername,
                    pools.generalPool(), pools.seatPoolByScheduleId());
        }

        for (int i = 0; i < 20; i++) {
            User customer = nextCustomer(customers, customerCursor);
            BookingDetailResponse booking = createDemoBooking(customer, passengersByUsername,
                    pools.generalPool(), pools.seatPoolByScheduleId());
            payForBooking(customer.getUsername(), booking);
        }

        for (int i = 0; i < 4; i++) {
            User customer = nextCustomer(customers, customerCursor);
            BookingDetailResponse booking = createDemoBooking(customer, passengersByUsername,
                    pools.generalPool(), pools.seatPoolByScheduleId());
            bookingService.cancelBooking(booking.booking().id());
        }

        for (int i = 0; i < 4; i++) {
            User customer = nextCustomer(customers, customerCursor);
            BookingDetailResponse booking = createDemoBooking(customer, passengersByUsername,
                    pools.farFlightPool(), pools.seatPoolByScheduleId());
            payForBooking(customer.getUsername(), booking);
            RefundResponse refund = SeedAuthContext.runAs(customer.getUsername(), "CUSTOMER",
                    () -> refundService.initiateRefund(booking.booking().id()));
            if (i < 2) {
                settleRefund(support, i, refund.id(), RefundDecision.APPROVE);
            }
        }

        for (int i = 0; i < 3; i++) {
            User customer = nextCustomer(customers, customerCursor);
            BookingDetailResponse booking = createDemoBooking(customer, passengersByUsername,
                    pools.midWindowPool(), pools.seatPoolByScheduleId());
            payForBooking(customer.getUsername(), booking);
            RefundResponse refund = SeedAuthContext.runAs(customer.getUsername(), "CUSTOMER",
                    () -> refundService.initiateRefund(booking.booking().id()));
            if (i < 2) {
                settleRefund(support, i, refund.id(), RefundDecision.APPROVE);
            }
        }

        for (int i = 0; i < 2; i++) {
            User customer = nextCustomer(customers, customerCursor);
            BookingDetailResponse booking = createDemoBooking(customer, passengersByUsername,
                    pools.farFlightPool(), pools.seatPoolByScheduleId());
            payForBooking(customer.getUsername(), booking);
            RefundResponse refund = SeedAuthContext.runAs(customer.getUsername(), "CUSTOMER",
                    () -> refundService.initiateRefund(booking.booking().id()));
            settleRefund(support, i, refund.id(), RefundDecision.REJECT);
        }
    }

    private User nextCustomer(List<User> customers, int[] cursor) {
        return customers.get(cursor[0]++ % customers.size());
    }

    private BookingDetailResponse createDemoBooking(
            User customer, Map<String, List<PassengerResponse>> passengersByUsername,
            List<Schedule> schedulePool, Map<Long, Deque<Seat>> seatPoolByScheduleId
    ) {
        for (Schedule schedule : schedulePool) {
            Deque<Seat> availableSeats = seatPoolByScheduleId.get(schedule.getId());
            if (availableSeats != null && !availableSeats.isEmpty()) {
                Seat seat = availableSeats.poll();
                PassengerResponse passenger = passengersByUsername.get(customer.getUsername()).get(0);
                CreateBookingRequest request = new CreateBookingRequest(
                        schedule.getId(), List.of(new SeatSelection(seat.getId(), passenger.id())), null);
                return bookingService.createBooking(customer.getUsername(), request);
            }
        }
        throw new IllegalStateException("Ran out of available seats while seeding demo bookings");
    }

    private void payForBooking(String username, BookingDetailResponse booking) {
        SeedAuthContext.runAs(username, "CUSTOMER", () -> paymentService.recordPayment(
                booking.booking().id(),
                new PaymentRequest(booking.booking().totalAmount(), "card", "SEED-PAY-" + booking.booking().id())));
    }

    private void settleRefund(List<User> support, int index, Long refundId, RefundDecision decision) {
        User supportAgent = support.get(index % support.size());
        SeedAuthContext.runAs(supportAgent.getUsername(), "SUPPORT",
                () -> refundService.processRefund(refundId, supportAgent.getUsername(), decision));
    }
}
