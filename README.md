# TicketWave

A ticket booking platform for travel (flights, buses, trains) and events —
search, seat selection, dynamic pricing, booking, payment, and refunds, with
role-based access for customers, operators, support agents, and admins.

Full product spec: [`genai.txt`](genai.txt). Repo-level coding standards
(layering, naming, error handling, testing): [`CLAUDE.md`](CLAUDE.md).

## Tech stack

| | |
|---|---|
| **Backend** | Spring Boot 4.0.6, Java 21, PostgreSQL, Liquibase, MapStruct, JWT, springdoc-openapi |
| **Frontend** | Angular 22, RxJS |
| **Testing** | JUnit 5 + Mockito, Testcontainers, Spring Cloud Contract, MockMvc, Vitest, Playwright, k6 |
| **Build** | Maven (backend), npm/Angular CLI (frontend) |

## Architecture

Backend packages are feature-based, under `com.ticketwave.<feature>`
(`auth`, `booking`, `catalog`, `payment`, `pricing`, `user`, `partner`,
`ledger`, `reporting`, `audit`, `ratelimit`, `common`, `config`, `devseed`).
Each feature follows a strict one-way dependency flow — no layer-skipping:

```
Controller -> Service (interface + impl) -> Repository -> Entity
                 |
                 v
         DTO <-> Entity via MapStruct mapper
```

- **Controllers** — HTTP concerns only (routing, status codes, `@Valid` binding). No business logic, no repository access.
- **Services** — all business logic and `@Transactional` boundaries. RBAC is enforced here via `@PreAuthorize`, not in controllers.
- **Repositories** — Spring Data JPA interfaces only.
- **Entities** — persistence model only; never returned from or accepted by a controller.
- **Mappers** — MapStruct, `@Mapper(componentModel = "spring")`.

Cross-cutting concerns live in `common` (the single `GlobalExceptionHandler`,
consistent error body) and `config` (security, caching, read-replica
routing, rate limiting).

### Core entities

`User`, `Passenger`, `Route`, `Schedule`, `Seat`, `Booking`, `BookingItem`,
`Payment`, `Refund` — plus supporting models for fare rules, promo codes,
partners/OAuth2 credentials, and the audit/ledger tables.

### Core flows

| Flow | Key classes |
|---|---|
| Search | `SearchController` → `ScheduleSearchServiceImpl` → `ScheduleSpecifications` |
| Dynamic pricing | `PricingServiceImpl` (base fare + seat-class + demand/time modifiers − promo discount) |
| Seat hold + expiration | `SeatHoldServiceImpl` (TTL-based hold) + `SeatHoldExpirationScheduler` (background sweep) |
| PNR generation | `PnrGeneratorImpl` (DB `UNIQUE` constraint is the source of truth) |
| Payment idempotency | `PaymentServiceImpl` — idempotent on `reference`; a replayed reference returns the original result instead of double-charging |
| Refunds & proration | `RefundServiceImpl` + `RefundPolicyService` (cancellation-window based proration) |

## API endpoints

70 endpoints across 20 `@RestController` classes, grouped by area:
Authentication · Search & schedules · Bookings/payments/reschedule ·
Refund settlement · Account (profile/preferences/passengers) · Operator
console (routes/schedules/vehicles/drivers/fares/reports) · Admin console
(users/partners/promos/audit/ledger) · Partner API (OAuth2 client
credentials).

- **Full endpoint reference:** [`docs/functional-specification.md`](docs/functional-specification.md) — method, path, summary, and required role for every endpoint.
- **Live/generated reference:** once the backend is running, `GET /v3/api-docs` (OpenAPI JSON) and `/swagger-ui.html` (interactive UI) always reflect the actual code.

Every error response has the same shape:

```json
{ "status": 404, "error": "BOOKING_NOT_FOUND", "message": "...", "timestamp": "..." }
```

## Setup instructions

### Prerequisites

- Java 21
- Maven (or use the repo's own `mvn` if a wrapper is added later)
- PostgreSQL 14+ (local install, or a container — no `docker-compose.yml` is checked in yet, so start one yourself, e.g. `docker run -e POSTGRES_USER=ticketwave -e POSTGRES_PASSWORD=ticketwave -e POSTGRES_DB=ticketwave -p 5432:5432 postgres:16`)
- Node.js + npm (`packageManager` pinned to `npm@11.16.0` in `frontend/package.json`)

### Backend

1. Create the database and set required environment variables. Everything
   has a sane local default (see `application.yml`) **except `JWT_SECRET`**,
   which has no default on purpose — startup fails fast rather than run with
   a guessable key:

   ```bash
   export JWT_SECRET="a-long-random-development-secret"
   # optional, only if your Postgres differs from the defaults:
   export DB_HOST=localhost DB_PORT=5432 DB_NAME=ticketwave \
          DB_USERNAME=ticketwave DB_PASSWORD=ticketwave
   ```

2. From `backend/`, run the app (Liquibase migrates the schema on boot):

   ```bash
   mvn spring-boot:run
   ```

   The API listens on `${SERVER_PORT:-8081}` — the port the frontend's dev
   proxy expects (see below).

3. **Optional — seeded demo data.** To populate every role, a route/schedule
   catalog across bus/train/flight/event, and bookings in every lifecycle
   state, run with the `seed` profile instead:

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=seed
   ```

   Every seeded account shares one password, logged at the end of the seed
   run: `SeedPass123!`. Usernames: `admin1-2`, `support1-3`, `operator1-6`,
   `customer1-30`.

### Frontend

```bash
cd frontend
npm install
npm start          # ng serve, http://localhost:4201
```

`frontend/proxy.conf.json` forwards `/api` to `http://localhost:8081`, matching
the backend's default port — so no extra configuration is needed as long as
both are left on their defaults.

## Testing guide

### Backend

```bash
cd backend
mvn clean verify
```

This one command runs everything the coverage gate depends on:

- **Unit tests** (Surefire) — services/controllers/mappers, JUnit 5 + Mockito.
- **Integration tests** (Failsafe, classes ending in `*IT`) — `@SpringBootTest` against a real PostgreSQL via Testcontainers (needs Docker, or point it at your local Postgres per the flow above).
- **Contract tests** (Spring Cloud Contract) — generated from `src/test/resources/contracts`.
- **Jacoco coverage check** — fails the build below the enforced thresholds:
  - ≥80% line coverage across the whole bundle.
  - 100% line **and** branch coverage on `PricingServiceImpl`, `SeatHoldServiceImpl`, `SeatHoldExpirationScheduler`, and `RefundPolicyService` specifically — the modules where a silent bug has direct financial impact.

The HTML coverage report lands at `backend/target/site/jacoco/index.html`
after the run.

Run only unit tests (skips Testcontainers/Docker):

```bash
mvn test
```

### Frontend

```bash
cd frontend
npm test            # Vitest unit/component tests
npm run e2e          # Playwright, against a running backend + frontend
```

### Load testing

```bash
k6 run load-test/search-and-seat-hold.js
```

Targets the highest-concurrency read/write paths (search browsing, seat
hold contention) against a running backend with seeded data. See
[`load-test/README.md`](load-test/README.md) for options.
