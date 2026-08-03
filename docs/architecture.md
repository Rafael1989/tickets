# TicketWave — Architecture

How the system is put together: the runtime topology, the backend layering rules
and how they are actually enforced, the cross-cutting concerns that sit outside
any one feature, and every configuration knob that changes behaviour.

Companion documents:
[`functional-specification.md`](functional-specification.md) (every endpoint),
[`data-model.md`](data-model.md) (persistence),
[`business-rules.md`](business-rules.md) (domain logic),
[`frontend.md`](frontend.md) (Angular app),
[`testing.md`](testing.md) (test strategy and coverage gates).

---

## 1. Runtime topology

```
                    ┌──────────────────────────┐
  Browser  ────────▶│  Angular 22 SPA          │
                    │  ng serve :4201          │
                    └────────────┬─────────────┘
                                 │  /api/**  (proxy.conf.json in dev)
                                 ▼
                    ┌──────────────────────────┐
  Partner  ────────▶│  Spring Boot 4.0.6 :8081 │
  (OAuth2 M2M)      │  stateless, JWT-bearer   │
                    └────────────┬─────────────┘
                                 │
                 ┌───────────────┼────────────────┐
                 ▼               ▼                ▼
        ┌────────────────┐ ┌───────────┐ ┌─────────────────┐
        │ PostgreSQL     │ │ Caffeine  │ │ Partner webhook │
        │ (primary)      │ │ in-proc   │ │ HTTP callbacks  │
        │ + optional     │ │ 30s TTL   │ │ (outbound)      │
        │   read replica │ └───────────┘ └─────────────────┘
        └────────────────┘
```

There is no message broker, no Redis, and no external payment gateway. Rate
limiting is in-process, caching is in-process, and card approve/decline is
simulated by `CardDeclineSimulator` from a handful of well-known test PANs. This
is a deliberate constraint of the stack, not an oversight — the places where it
matters are called out in the code and in [`business-rules.md`](business-rules.md).

**Ports.** Backend `${SERVER_PORT:-8081}`, frontend dev server `4201`. The dev
proxy in `frontend/proxy.conf.json` forwards `/api` to `http://localhost:8081`,
so the two defaults line up with no extra configuration.

---

## 2. Backend layering

The dependency flow is strictly one-way, with no layer-skipping:

```
Controller ──▶ Service (interface + impl) ──▶ Repository ──▶ Entity
                    │
                    ▼
            DTO ◀──▶ Entity   (MapStruct mapper)
```

| Layer | Responsibility | Explicitly not allowed |
|---|---|---|
| **Controller** | Routing, status codes, `@Valid` binding, OpenAPI annotations | Business logic; touching a repository; returning an entity |
| **Service** | All business logic; `@Transactional` boundaries; `@PreAuthorize` role checks | HTTP concerns; hand-rolled DTO mapping |
| **Repository** | Spring Data JPA interfaces, `@Query`, specifications | Business logic; DTO mapping |
| **Entity** | Persistence model | Crossing the API boundary in either direction |
| **Mapper** | MapStruct `@Mapper(componentModel = "spring")` | Living inside a service or entity |

Packages are **feature-based**, not layer-based — `com.ticketwave.<feature>.<layer>`:

```
com.ticketwave.booking.controller
com.ticketwave.booking.service
com.ticketwave.booking.repository
com.ticketwave.booking.entity
com.ticketwave.booking.dto
com.ticketwave.booking.mapper
```

### Feature packages

| Package | Owns |
|---|---|
| `auth` | Registration, login, JWT issuing/parsing, the authentication filter |
| `user` | Accounts, roles, preferences, saved passenger profiles |
| `catalog` | Routes, schedules, seats, vehicles, drivers, search, seat holds |
| `booking` | Booking lifecycle, booking items, PNR generation |
| `payment` | Payments, 3DS simulation, refunds, cancellation policy, reschedule |
| `pricing` | Dynamic fare calculation, fare rules, promo codes |
| `partner` | Partner onboarding, OAuth2 client credentials, outbound webhooks |
| `ledger` | Append-only financial ledger, reconciliation reporting |
| `reporting` | Operator revenue/occupancy reports |
| `audit` | Append-only audit log and its search |
| `ratelimit` | Token-bucket filter and its configuration |
| `common` | `GlobalExceptionHandler`, `TicketwaveException`, `ErrorResponse` |
| `config` | Security, caching, datasource routing, typed `@ConfigurationProperties` |
| `devseed` | `@Profile("seed")` dev fixture loader — never runs in a normal boot |

Current inventory: **20 controllers / 69 endpoints**, 24 service implementations,
20 repositories, 18 MapStruct mappers, 43 types under `entity` packages
(entities, enums, and their JPA attribute converters).

---

## 3. Security

### Authentication

Stateless JWT bearer tokens; `SessionCreationPolicy.STATELESS`, CSRF disabled
(there is no cookie-borne session to forge against). `JwtAuthenticationFilter`
runs before `UsernamePasswordAuthenticationFilter`. Passwords are BCrypt-hashed.

`JWT_SECRET` has **no default value** in `application.yml` — startup fails fast
if it is unset, rather than running with a secret that is public in source
control.

> A subtlety worth knowing before you touch it: `JwtAuthenticationFilter` is a
> `@Component` *and* a `jakarta.servlet.Filter`, so Spring Boot would register it
> a second time as a global servlet filter on top of the intentional
> `addFilterBefore` wiring. `SecurityConfig.jwtAuthenticationFilterRegistration`
> disables that duplicate registration. Without it, the early pass runs outside
> the security chain, its authentication is discarded, and
> `OncePerRequestFilter`'s own guard then skips the correct pass — net effect,
> authentication is silently never set. `RateLimitingFilter` avoids the same trap
> by deliberately *not* being a `@Component`.

### Authorization

Role checks live on **service methods** via `@PreAuthorize` (`@EnableMethodSecurity`),
not as scattered `if` statements against the principal. Four human roles —
`CUSTOMER`, `OPERATOR`, `SUPPORT`, `ADMIN` — plus a non-human `PARTNER_API` role
carried by machine-to-machine tokens.

Two recurring authorization idioms:

- **Ownership** — `@bookingOwnership.isOwnedBy(#bookingId, authentication.name)`,
  usually OR-ed with `hasAnyRole('SUPPORT','ADMIN')`.
- **Tenancy** — `TenantScope.isSameTenant(resourceOwner, caller)` for
  operator-managed inventory. True for the resource's own creator, or for any
  other operator sharing the same non-null `Partner`, so a partner company's
  staff collectively manage its inventory. A standalone operator
  (`partner == null`) only ever matches itself.

Operator-scoped lookups apply the tenant filter *inside* the `Optional` chain and
throw `NotFound`, so a foreign-owned resource **404s rather than 403s** — the API
never confirms that someone else's resource exists.

Public endpoints are enumerated in `config/PublicEndpoints.java` (auth, partner
token, catalog reads, guest booking lookup, promo preview) plus the springdoc and
health paths.

### Rate limiting

`RateLimitingFilter` runs ahead of the security chain. It keys public paths by
client IP and partner-API paths by the `clientId` subject of the caller's
`PARTNER_API` token (falling back to IP when no valid such token is present).
Because it runs before Spring Security, it parses the bearer token itself rather
than reading `SecurityContextHolder`.

Exceeding the budget returns `429` with a `Retry-After` header and the standard
error body shape.

`server.forward-headers-strategy: native` lets embedded Tomcat rewrite
`getRemoteAddr()` from `X-Forwarded-For`, but only when the immediate TCP peer
matches Tomcat's internal-proxy ranges — so a direct client cannot spoof the
header to dodge the limit.

---

## 4. Error handling

A single `@RestControllerAdvice` (`GlobalExceptionHandler`) is the only thing
that turns an exception into a response body. No raw exception or stack trace
ever reaches a client.

```json
{ "status": 404, "error": "BOOKING_NOT_FOUND", "message": "...", "timestamp": "..." }
```

| Exception | Status | `error` code |
|---|---|---|
| `TicketwaveException` (base of every domain exception) | its own | its own (e.g. `SEAT_UNAVAILABLE`) |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` (+ per-field `details`) |
| `ObjectOptimisticLockingFailureException` | 409 | `CONCURRENT_UPDATE` |
| `AccessDeniedException` | 403 | `ACCESS_DENIED` |
| anything else | 500 | `INTERNAL_ERROR` (logged at ERROR) |

Services throw specific exceptions (`SeatUnavailableException`,
`BookingNotFoundException`, …), never a bare `RuntimeException`. Request
validation is Bean Validation on the DTO at the controller boundary, not
hand-rolled null checks in services.

---

## 5. Persistence concerns

**Migrations.** Liquibase, one logical change per changeset, files named
`YYYY-MM-DD-NN-description.xml` under
`src/main/resources/db/changelog/changes/`. A shipped changeset is never edited —
a new one is added. Hibernate runs with `ddl-auto: validate`, so the entities and
the migrated schema must agree or the app refuses to start.

Liquibase deliberately bypasses the routing datasource: `spring.liquibase.url`
is pinned to `${spring.datasource.url}` so migrations always run against the
primary.

**`open-in-view: false`.** Entities are detached once a service method's
transaction closes. Two consequences show up repeatedly in this codebase and are
worth internalising:

1. Mutating a detached entity is inert — it must be explicitly `save`d.
   (`PaymentServiceImpl.confirmThreeDs` re-saves for exactly this reason.)
2. Lazy associations cannot be walked after the fact. `LedgerServiceImpl`
   re-fetches the booking inside its own transaction rather than trusting the
   passed-in `Payment`'s lazy graph.

**Read-replica routing.** `ReadWriteRoutingDataSource` routes any
`@Transactional(readOnly = true)` method to the replica pool, wrapped in a
`LazyConnectionDataSourceProxy` so the routing decision is evaluated *after*
Spring's transaction interceptor has set the read-only flag. Until
`ticketwave.datasource.replica.url` is set, the replica pool points at the same
connection as the primary — a genuine no-op, not a half-built feature.

`@Primary` on the routing `DataSource` bean is load-bearing, not decorative:
without it there are three unqualified `DataSource` beans, Spring Boot's
`@ConditionalOnSingleCandidate` silently fails, and the entire JPA layer never
configures.

**Caching.** Caffeine, 30-second TTL, max 2 000 entries, two caches
(`scheduleSearchIds`, `scheduleStaticInfo`). Only static schedule/route fields
are cached — **seat availability is never cached** and always read fresh. The
TTL only bounds how quickly an edit that does not trigger an explicit
`@CacheEvict` becomes visible.

---

## 6. Configuration reference

Everything is environment-overridable. Defaults shown are the ones in
`application.yml`.

| Property | Env var | Default | Effect |
|---|---|---|---|
| `ticketwave.jwt.secret` | `JWT_SECRET` | **none — required** | HMAC signing key; startup fails if unset |
| `ticketwave.jwt.access-token-ttl-minutes` | `JWT_ACCESS_TTL_MINUTES` | `15` | Access token lifetime |
| `ticketwave.jwt.refresh-token-ttl-minutes` | `JWT_REFRESH_TTL_MINUTES` | `10080` | Refresh token lifetime |
| `ticketwave.inventory.seat-hold-ttl-minutes` | `SEAT_HOLD_TTL_MINUTES` | `10` | How long a seat hold survives |
| `ticketwave.inventory.hold-sweep-interval-ms` | `SEAT_HOLD_SWEEP_INTERVAL_MS` | `60000` | Expired-hold sweeper period |
| `ticketwave.pricing.last-minute-threshold-hours` | `PRICING_LAST_MINUTE_THRESHOLD_HOURS` | `24` | Below this → surcharge |
| `ticketwave.pricing.last-minute-surcharge-rate` | `PRICING_LAST_MINUTE_SURCHARGE_RATE` | `0.25` | +25% |
| `ticketwave.pricing.early-bird-threshold-days` | `PRICING_EARLY_BIRD_THRESHOLD_DAYS` | `30` | Above this → discount |
| `ticketwave.pricing.early-bird-discount-rate` | `PRICING_EARLY_BIRD_DISCOUNT_RATE` | `0.10` | −10% |
| `ticketwave.pricing.high-occupancy-threshold` | `PRICING_HIGH_OCCUPANCY_THRESHOLD` | `0.80` | ≥80% taken → surcharge |
| `ticketwave.pricing.high-occupancy-surcharge-rate` | `PRICING_HIGH_OCCUPANCY_SURCHARGE_RATE` | `0.15` | +15% |
| `ticketwave.pricing.low-occupancy-threshold` | `PRICING_LOW_OCCUPANCY_THRESHOLD` | `0.20` | ≤20% taken → discount |
| `ticketwave.pricing.low-occupancy-discount-rate` | `PRICING_LOW_OCCUPANCY_DISCOUNT_RATE` | `0.05` | −5% |
| `ticketwave.refund.full-refund-threshold-days` | `REFUND_FULL_THRESHOLD_DAYS` | `7` | ≥7 days out → 100% |
| `ticketwave.refund.partial-refund-threshold-hours` | `REFUND_PARTIAL_THRESHOLD_HOURS` | `24` | ≥24h out → partial |
| `ticketwave.refund.partial-refund-rate` | `REFUND_PARTIAL_RATE` | `0.50` | 50% |
| `ticketwave.rate-limit.requests-per-window` | `RATE_LIMIT_REQUESTS_PER_WINDOW` | `60` | Token bucket size |
| `ticketwave.rate-limit.window-seconds` | `RATE_LIMIT_WINDOW_SECONDS` | `60` | Window length |
| `ticketwave.datasource.replica.*` | `DB_REPLICA_URL` / `_USERNAME` / `_PASSWORD` | blank | Read-replica target; blank ⇒ primary |
| `spring.datasource.*` | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | localhost/5432/ticketwave | Primary database |
| Hikari pool | `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_POOL_CONNECTION_TIMEOUT_MS`, `DB_POOL_IDLE_TIMEOUT_MS`, `DB_POOL_MAX_LIFETIME_MS` | 10 / 5 / 30s / 10m / 30m | Connection pool |
| `server.port` | `SERVER_PORT` | `8081` | HTTP port |

Each group is bound to a typed record in `config/` (`JwtProperties`,
`InventoryProperties`, `PricingProperties`, `RefundProperties`,
`RateLimitProperties`, `ReplicaDataSourceProperties`) rather than read as loose
`@Value` strings.

### Spring profiles

| Profile | Effect |
|---|---|
| *(none)* | Normal boot |
| `seed` | Runs `DevDataSeeder` against a database that does not already look seeded — users for every role, a bus/train/flight/event catalog, and bookings in every lifecycle state. Shared password `SeedPass123!` |

---

## 7. API surface

69 endpoints across 20 controllers. Every one carries springdoc
`@Operation`/`@ApiResponses` annotations, written as the endpoint is written
rather than retrofitted.

- **Human-readable reference:** [`functional-specification.md`](functional-specification.md)
- **Generated, always in sync:** `GET /v3/api-docs`, rendered at `/swagger-ui.html`

Conventions: plural-noun resource paths (`/api/bookings`,
`/api/schedules/{id}/seats`); idempotency keys required on payment-initiating
endpoints; entities never cross the boundary — always a purpose-named DTO
(`CreateBookingRequest` / `BookingResponse`), never one DTO reused for both
directions.
