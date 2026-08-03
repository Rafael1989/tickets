# TicketWave — Testing

The test strategy, the coverage gates the build enforces, and what is measured
versus what is deliberately excluded.

---

## 1. Test layers

### Backend

| Layer | Tooling | Scope | Runner |
|---|---|---|---|
| **Unit** | JUnit 5 + Mockito + AssertJ | Services, mappers, specifications, filters, converters — dependencies mocked | Surefire (`mvn test`) |
| **Controller** | `@WebMvcTest` + MockMvc | Request validation, status codes, error-body shape — **not** business logic | Surefire |
| **Integration** | `@SpringBootTest` against local PostgreSQL | Repository queries and full booking/payment/refund flows | Failsafe (`*IT`, `mvn verify`) |
| **Contract** | Spring Cloud Contract | Generated from `src/test/resources/contracts` | Failsafe |

100 test classes: **670 unit/controller tests** (Surefire) and **35 integration
tests** (Failsafe), all passing.

The 8 integration classes are `TicketwaveApplicationIT`, `AuthenticationFlowIT`,
`BookingFlowIT`, `PaymentFlowIT`, `RefundFlowIT`, `SeatHoldConcurrencyIT`,
`ScheduleSpecificationsIT` and `PartnerOnboardingIT`.

**Real PostgreSQL, never H2.** Liquibase-managed schema, PostgreSQL-specific
transaction-abort behaviour on constraint violations, `SELECT … FOR UPDATE`
semantics and enum/JSON column handling all diverge from an in-memory database —
and three of the four idempotency mechanisms depend on exactly those behaviours.
An H2-backed test would pass while the production path was broken.

**Testcontainers.** `mvn verify` therefore **requires a running Docker**. One
`postgres:16` container is started per test JVM by
[`PostgresContainerSupport`](../backend/src/test/java/com/ticketwave/PostgresContainerSupport.java)
and torn down by Ryuk when the JVM exits. Nothing outside the run is touched:
there is no configured database URL a forgotten env var could resolve to, so a
test run cannot reach a real database at all.

A hand-rolled singleton, not `@Testcontainers` + `@Container`: the annotations
start and stop a container per test class, which here would mean eight
containers and eight Liquibase runs per build, buying no isolation these tests
need. `ScheduleSpecificationsIT` is `@DataJpaTest` and cannot extend
`AbstractIntegrationTest` — mutually exclusive bootstrap strategies — so it
registers the same singleton itself.

`AbstractIntegrationTest` is still deliberately **not** `@Transactional`:
concurrency tests spawn worker threads and HTTP-level tests run on Tomcat's
request thread, neither of which a test-thread rollback would cover. And
`uniqueSuffix()` still matters, for a narrower reason than before — the
container is per JVM, so every class in a run shares one database and a
hardcoded fixture identifier still collides with a sibling test, just no longer
with yesterday's run.

> **Windows / Docker Desktop caveat.** The build pins `api.version=1.44` for the
> Failsafe JVM (see `backend/pom.xml`). Without it Testcontainers negotiates the
> connection fine — it even logs `API Version: 1.55` — but the image-pull command
> still goes out as client version 1.32, and Docker Engine 26+ rejects it with
> *"client version 1.32 is too old"*. The symptom moves around depending on
> whether `~/.testcontainers.properties` has cached a client strategy, which
> makes it look like several unrelated faults. It is one, and the pinned property
> is the fix. Note it is docker-java's own `api.version`, **not** the
> `DOCKER_API_VERSION` environment variable, which docker-java namespaces
> differently and which does not help.

### Frontend

| Layer | Tooling | Scope |
|---|---|---|
| Unit / component | Vitest (via `@angular/build:unit-test`) + Angular TestBed | 57 spec files, **401 tests**, all passing — services, guards, interceptors, components |
| End-to-end | Playwright + Chromium | **5 tests**, all passing — `auth.spec.ts` (4) and `booking-golden-path.spec.ts` (1, search → book → pay) |

`npm test` runs in **watch mode** and does not exit. For a one-shot or CI run:

```bash
npx ng test --watch=false
```

The run prints repeated `Not implemented: HTMLCanvasElement's getContext()`
warnings — jsdom lacking a canvas implementation, triggered by the QR-code
component. Noise, not failures; the suite still reports all 401 green.

Highest-value frontend cases, per the repo standards: seat-hold countdown,
checkout validation, and error-state rendering.

### Load

`k6 run load-test/search-and-seat-hold.js` — targets the highest-concurrency
read/write paths (search browsing, seat-hold contention) against a running
backend with seeded data.

---

## 2. Conventions

**Naming:** `methodName_condition_expectedResult`, e.g.
`holdSeat_whenAlreadyHeld_throwsSeatUnavailableException`,
`isSameTenant_whenPartnersDiffer_returnsFalse`.

**Every bug fix ships with a regression test** reproducing the original failure.
Several such tests are annotated in place with the bug they pin down — for
instance the `confirmThreeDs` detached-entity save, where the response reported
`SUCCEEDED` while the row stayed `PENDING_3DS`.

**Controller tests assert the contract, not the logic.** Business rules belong in
the service unit test; the `@WebMvcTest` verifies that a malformed request is a
400 with the right error body, and that the right status code comes back.

---

## 3. Coverage gates

JaCoCo runs at the `verify` phase, on unit **and** integration coverage merged,
and **fails the build** below these thresholds:

| Scope | Counter | Minimum |
|---|---|---|
| Whole bundle | Line | 80% |
| Whole bundle | Branch | 80% |
| `PricingServiceImpl` | Line **and** branch | 100% |
| `SeatHoldServiceImpl` | Line **and** branch | 100% |
| `SeatHoldExpirationScheduler` | Line **and** branch | 100% |
| `RefundPolicyService` | Line **and** branch | 100% |

Those four classes are pinned at 100% because a silent bug in pricing, seat holds
or refund proration has direct financial impact — it produces a wrong number
rather than a visible error.

### Current measured coverage

Measured from `mvn clean verify` — unit **and** integration coverage merged:

| Counter | Ratio | Missed |
|---|---|---|
| **Branch** | **98.05%** | 9 |
| **Line** | **99.34%** | 14 |

For reference, the Surefire suite alone reaches 96.53% branch / 97.50% line. The
`*IT` classes are worth roughly +1.5pt branch and +1.8pt line — a modest
contribution, and deliberately so: they exist to prove concurrency and
PostgreSQL-specific behaviour, not to move a percentage.

Two HTML reports are produced:

| Path | Contents |
|---|---|
| `target/site/jacoco-merged/index.html` | **Authoritative** — unit + integration, and what the gate grades |
| `target/site/jacoco/index.html` | Unit-only, written at the `test` phase for fast feedback from `mvn test` |

### What is excluded, and why

Coverage is measured on hand-written production code only. Two exclusions are
configured in `backend/pom.xml`:

| Excluded | Reason |
|---|---|
| `**/*MapperImpl.class` | MapStruct-generated into `target/generated-sources`, with a null-guard branch per mapped property. Generated code, not authored code |
| `com/ticketwave/devseed/**` | `DevDataSeeder` is a `@Profile("seed")` dev-fixture loader that never runs outside a developer's local database |

`backend/lombok.config` sets `lombok.addLombokGeneratedAnnotation = true`, which
marks Lombok-generated members with `@lombok.Generated` — JaCoCo honours that
annotation and skips them.

This matters for reading the number honestly: before these exclusions the bundle
reported **74.28%** branch coverage, and roughly two thirds of all missed
branches belonged to generated or dev-only code. The exclusions are what carried
the bundle past the 80% bar; targeted tests then closed ~50 genuinely untested
branches on top, leaving only the deliberate ones below. Neither fact is hidden
by the reported figure — it is simply measuring a different, narrower set of
classes.

### Known gaps

9 branches remain uncovered in the merged report, none of them in the
financially-pinned classes — and **every one of them is deliberate**: each is
either structurally unreachable or reachable only by a test that asserts
nothing.

| Area | Branches | Why it stays uncovered |
|---|---|---|
| `@PrePersist onCreate()` — the already-set arm, across six entities | 6 | The *"`createdAt` was already populated"* path. Reaching it means constructing an entity with the timestamp pre-set purely to satisfy the counter |
| `ScheduleController#callerUsername` — the anonymous and not-authenticated arms | 2 | Spring MVC resolves a bare `Authentication` parameter from `getUserPrincipal()`, and `SecurityContextHolderAwareRequestWrapper` already nulls anonymous authentications, so the controller never sees one. Kept as defence in depth |
| `PaymentServiceImpl#markPaymentProcessingWithRetry` — the loop's exit-by-condition arm | 1 | Unreachable by construction: every iteration either returns or, on the final attempt, rethrows |

Each of the three is marked with a "note for coverage readers" comment at the
site itself, so this table and the code cannot drift apart silently.

---

## 4. How coverage is wired

Both test runs are measured, into **separate** exec files, then merged:

| Phase | Execution | Writes |
|---|---|---|
| `initialize` | `prepare-agent` | agent → `jacoco.exec` (surefire), published as `${argLine}` |
| `pre-integration-test` | `prepare-agent-integration` | agent → `jacoco-it.exec`, published as `${failsafeArgLine}` |
| `test` | `report` | unit-only HTML, fast feedback |
| `post-integration-test` | `merge-all` | `jacoco-merged.exec` |
| `verify` | `report-merged` + `check` | authoritative report **and** the gate |

Two details are load-bearing:

- **`propertyName` must be set explicitly.** `prepare-agent-integration`
  defaults to publishing into `${argLine}` — the same property
  `prepare-agent` already used — so without naming it `failsafeArgLine` and
  pointing failsafe at that, which agent lands on which run depends on
  execution order. Getting this wrong is silent: the build passes, `jacoco-it.exec`
  is simply never created, and the merged report equals the unit-only one
  byte-for-byte.
- **`check` runs at `verify`, on the merged data.** Bound to `test` (as it
  originally was) it ran *before* `integration-test`, so the `*IT` classes could
  not contribute a single covered line to the gate they were meant to help
  satisfy.

`mvn test` alone still produces the unit-only report, but no longer runs the
gate — `mvn verify` is what enforces it.

**Always use `clean`.** JaCoCo appends to an existing exec file by default, so a
stale one from an earlier run inflates the numbers.

---

## 5. Running the tests

```bash
cd backend

mvn clean test      # unit + controller tests, unit-only coverage report
mvn clean verify    # the above, plus the *IT suite, contract tests, and the coverage gate
```

`verify` needs **Docker running** and nothing else — no database to create, no
env vars to export. Testcontainers pulls `postgres:16` on first use and starts a
throwaway instance per run (§1).

`docker-compose.yml` is still here, but it is now only for **running the app**:
it creates `ticketwave` (dev) and `ticketwave_e2e` (Playwright), which the *IT*
suite no longer needs.

```bash
docker compose up -d postgres   # dev + e2e databases; the IT suite is independent of it
```

Two caveats for that compose file:

- **Port 5432 is often already bound** by a natively installed PostgreSQL. If
  the container fails to bind, either stop that service or run with
  `DB_PORT=5433` exported for both `docker compose` and the app.
- **The init script runs only on first initialisation** of an empty volume. If
  you started the container before it existed, the extra databases are missing;
  `docker compose down -v` (destroys the data) or a manual `CREATE DATABASE`
  fixes it.

Liquibase builds the schema inside each database on first run.

```bash
cd frontend
npx ng test --watch=false   # Vitest, one shot (npm test watches and never exits)
```

### End-to-end (Playwright)

CI runs this suite in its own job (see §6). Locally there are three
prerequisites, none of them automatic — `playwright.config.ts` has no
`webServer` entry, so nothing is started for you.

**1. A dedicated `ticketwave_e2e` database.** `global-setup.ts` **truncates every
table** it finds, so it must never point at `ticketwave` (dev) or
`ticketwave_test` (the `*IT` suite). Liquibase creates tables but not the
database, so create it once — `docker compose up -d postgres` already does,
otherwise:

```sql
CREATE DATABASE ticketwave_e2e;
```

`global-setup.ts` honours `DB_HOST`, `DB_PORT`, `E2E_DB_NAME`, `DB_USERNAME` and
`DB_PASSWORD`, defaulting to the values above. The database name has its own
variable rather than reusing `DB_NAME` on purpose: sharing one would let a
`DB_NAME=ticketwave` left over from a backend run point the truncation at the
dev database.

**2. Backend on 8081, pointed at that database:**

```bash
cd backend
JWT_SECRET=e2e-only-secret-key-at-least-32-bytes-long \
DB_NAME=ticketwave_e2e DB_USERNAME=postgres DB_PASSWORD=root SERVER_PORT=8081 \
mvn spring-boot:run
```

**3. Frontend on 4201** (`npm start`), then:

```bash
cd frontend
npx playwright test --config=e2e/playwright.config.ts
```

The suite runs single-worker on purpose: every spec shares one seeded
schedule/seat fixture, and parallel workers would race for it.

**Never probe an `/api/...` resource to decide the backend is ready.** The
backend caches `scheduleStaticInfo` per schedule id for 30s, and
`global-setup.ts` truncates with `RESTART IDENTITY`, so the seeded schedule is
always id 1. A readiness loop hitting `GET /api/schedules/1` before the seed
therefore caches an empty result under exactly the key the suite is about to
need: the search then finds the id, gets the stale empty back, and answers 200
with an empty list. The golden-path test fails on a cache entry the readiness
check itself planted, and only when the whole sequence fits inside the 30s TTL
— which is why it survived three local runs and failed in CI. Probe
`/v3/api-docs` instead: no schedule data, and outside `/api/*`, so it also
stays out of the shared rate-limit bucket.

```bash
k6 run load-test/search-and-seat-hold.js
```

Always use `clean` when you care about the coverage number, for the append reason
above.

---

## 6. CI

`.github/workflows/ci.yml` runs on every push and pull request to `main`, in
three parallel jobs:

| Job | Does |
|---|---|
| **backend** | `mvn -B clean verify` against a `postgres:16` service container — full suite plus the coverage gate. Uploads the merged JaCoCo report always, and Surefire/Failsafe reports on failure |
| **frontend** | `npm ci`, `npx ng test --watch=false`, `npm run build` |
| **e2e** | Builds the backend jar, boots it on 8081 against a `ticketwave_e2e` service container, serves the frontend on 4201, then runs the Playwright suite. Uploads traces, reports and both server logs on failure |

Three details carried over from the problems documented above:

- The workflow runs `verify`, not `test`. `mvn test` skips every `*IT` and runs
  no gate — which is precisely how a non-executing integration suite stayed
  invisible.
- A `concurrency` group cancels superseded runs on the same ref, because the ITs
  share one database and overlapping runs can interfere.
- The **e2e** job starts both servers itself and polls until each answers,
  rather than relying on a `webServer` entry in `playwright.config.ts` (which
  deliberately has none — see its Javadoc). Its database is a third service
  container, never shared with the backend job's, because `global-setup.ts`
  truncates every table it finds.

CI runs PostgreSQL as a service container. Locally you can use either a natively
installed server or the optional `docker-compose.yml` — nothing in the build
requires Docker either way.
