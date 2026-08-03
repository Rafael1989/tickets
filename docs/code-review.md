# TicketWave — How the Code Review Was Conducted

The review methodology applied to this system, the findings it produced, and the
evidence that each correction actually landed.

---

## A note on provenance

**This document is reconstructed, not transcribed.** The review sessions
themselves were interactive and their chat transcripts were not retained. What
this document is built from is two durable sources:

1. **`prompts`** (repo root) — the step-by-step prompt log, which records the
   review phases that were executed and marks each one `Done`.
2. **The codebase itself** — this repo carries an unusually high density of
   explanatory comments that name the defect they exist to prevent. Those
   comments are the primary evidence for the findings listed below.

Every finding in §4 carries a `file:line` citation so it can be verified against
the code rather than taken on trust. What is *not* claimed here is the exact
wording, ordering, or severity label assigned during the original session — those
were not preserved. Severities below are assigned in this document from the
demonstrated impact, and are labelled as such.

---

## 1. Review methodology

The review was not a single pass. It was structured as **repeated, differently-framed
audits**, each narrow enough to be answered concretely.

### 1.1 Two-step pattern

Every review phase used the same two-prompt structure — deliberately splitting
*finding* from *fixing*, so the finding list existed as an artifact before any
code changed:

| Step | Prompt |
|---|---|
| **Find** | "Review … for best-practice violations and code smells, and **list the findings ranked by severity**" |
| **Fix** | "**Apply the critical corrections** identified in the code review" |

### 1.2 Phases executed

| Phase | Scope | Prompts |
|---|---|---|
| **Phase 7** | Backend only, after the core business logic and API existed | `P16` find → `P17` apply |
| **Phase 10** | Full stack (Spring Boot + Angular), after the frontend existed — explicitly widened to **security flaws and performance bottlenecks**, not just code smells | `P21` find → `P22` apply |

Reviewing twice, at different maturity points, is what caught the two classes of
defect that a single end-of-project pass tends to miss: backend concurrency
issues (invisible while the surface area is small) and cross-stack integration
issues (invisible until a real client exists).

### 1.3 Axis 1 — audit by User Story

Each of the eight user stories was audited end-to-end as a vertical slice —
backend endpoint, DB model, business logic, **and** Angular component — with a
forced verdict per story:

> "Output a checklist summary (**PASS / FAIL / PARTIAL**) for each User Story,
> highlighting any missing endpoints, frontend routes, or test cases."

Forcing a three-value verdict (rather than a prose summary) is what surfaced
`PARTIAL` items that a narrative review would have glossed over.

### 1.4 Axis 2 — audit by layer

The same system was then re-reviewed along architectural seams, each with its own
verdict vocabulary:

| Audit | Verdict scale | Focus |
|---|---|---|
| **DATABASE** | `MATCH / MISMATCH / MISSING` | Entity-attribute conformance to spec, FK constraints, indexes, enum mapping, precision/scale on money, Liquibase↔JPA drift |
| **BACKEND** | `IMPLEMENTED / PARTIAL / MISSING` | Endpoint/verb conformance, OpenAPI alignment, `@PreAuthorize` RBAC coverage, `@Valid` + `@ControllerAdvice`, DTO↔entity decoupling |
| **FRONTEND** | `IMPLEMENTED / PARTIAL / MISSING` | Page/feature coverage, route guards per role, interceptor-based error handling, reactive form validation |
| **DEVELOPMENT** | `VERIFIED / PARTIAL / MISSING` | Caching, connection pooling, read-replica readiness, rate limiting, idempotency, seat-hold locking, pricing engine, PNR entropy, ledger/reconciliation, coverage, contract tests, load tests |

Auditing the same code twice along **orthogonal axes** — vertical (per story) and
horizontal (per layer) — is the core of the method. A defect invisible from one
axis tends to be obvious from the other: the missing Failsafe wiring (§4.1) is
not a user-story problem at all and only appears in a build/testing audit.

### 1.5 Role-based framing

Later audits pinned an explicit reviewer persona to raise the standard applied —
e.g. *"Act as a Senior Full-Stack Engineer, Security Specialist, and UX/UI
Designer specialized in Angular, Financial Integration Architecture, and Scalable
Backend Systems."* The security-specialist framing is what drove the audit to ask
about idempotency keys, webhook signature validation and PCI scope reduction
rather than only about naming and layering.

### 1.6 Standards baseline

All review passes were graded against a **written, repo-level standard** —
[`CLAUDE.md`](../CLAUDE.md), generated in Phase 0 *before* any code existed
(`P0`). Reviews therefore checked conformance to a fixed rubric (layering rules,
naming, error-body shape, `BigDecimal` for money, test naming, coverage targets)
instead of to a reviewer's momentary taste.

---

## 2. What was examined

| Area | Inventory |
|---|---|
| Controllers / endpoints | 20 / 69 |
| Service implementations | 24 |
| Repositories | 20 |
| MapStruct mappers | 18 |
| Entity-package types | 43 |
| Liquibase changelogs | 33 |
| Backend test classes | 100 (670 unit + 35 integration) |
| Angular spec files | 57 (401 tests) |
| Playwright e2e specs | 2 |

---

## 3. Severity model

| Severity | Meaning |
|---|---|
| **Critical** | Silent incorrectness — wrong money, lost authentication, or double-booking, with no error surfaced |
| **High** | Security exposure, data-integrity risk, or a guarantee the code claims but does not deliver |
| **Medium** | Correctness under concurrency or edge conditions; misleading behaviour |
| **Low** | Performance, maintainability, documentation accuracy |

The recurring theme across critical findings is **silence**. Nearly every one
produced no exception, no log line, and no failing test — the system simply did
the wrong thing. That is the class of defect the review was most valuable against.

---

## 4. Findings and corrections

### 4.1 Critical

**Duplicate servlet-filter registration silently disabled authentication.**
`JwtAuthenticationFilter` is both a `@Component` and a `jakarta.servlet.Filter`,
so Spring Boot auto-registered it globally *in addition to* the intentional
`addFilterBefore` wiring. The early pass ran outside the security chain, its
authentication was discarded, and `OncePerRequestFilter`'s own guard then skipped
the correct pass. Net effect: authentication was never set — with no error.
→ Fixed by a disabled `FilterRegistrationBean`,
[SecurityConfig.java:70](../backend/src/main/java/com/ticketwave/config/SecurityConfig.java#L70).

**The same trap, avoided by design in the rate limiter.** `RateLimitingFilter` is
deliberately *not* a `@Component`, wired through exactly one
`FilterRegistrationBean`. The comment names the earlier bug explicitly —
evidence the review's finding was generalised into a rule, not just patched once.
[RateLimitingFilter.java:26](../backend/src/main/java/com/ticketwave/ratelimit/RateLimitingFilter.java#L26).

**Missing `@Primary` would silently disable the entire JPA layer.** With
`primaryDataSource`, `replicaDataSource` and the routing bean all typed
`DataSource`, Spring Boot's `@ConditionalOnSingleCandidate(DataSource.class)`
fails to resolve, `entityManagerFactory` is never created, and startup fails with
every repository reporting a missing bean — an error that never mentions the
class responsible.
[DataSourceRoutingConfig.java:62-76](../backend/src/main/java/com/ticketwave/config/DataSourceRoutingConfig.java#L62-L76).

**3DS confirmation never persisted its result.** With `open-in-view: false` the
`Payment` was detached by the time `confirmThreeDs` mutated it, so the setters
were inert. The API returned `SUCCEEDED` while the row stayed `PENDING_3DS`
forever. → Explicit re-`save`, plus a regression test set that exists specifically
to catch it.
[PaymentServiceImpl.java:179-183](../backend/src/main/java/com/ticketwave/payment/service/PaymentServiceImpl.java#L179-L183).

**Integration tests were never actually running.** No Failsafe execution was
bound, and Surefire's default patterns do not match `*IT` — so `mvn verify`
silently skipped every `*IT` class. The suite reported green while its
highest-value tests never executed.
→ Failsafe plugin wired with `integration-test` + `verify` goals,
[pom.xml:210-227](../backend/pom.xml#L210-L227).

### 4.2 High

**Concurrent refund requests could both succeed.** Two requests could each read
`CONFIRMED` before either committed, producing duplicate refunds. → `@Version`
optimistic lock on `Booking`, mapped to `409 CONCURRENT_UPDATE`.
[Booking.java:77-87](../backend/src/main/java/com/ticketwave/booking/entity/Booking.java#L77-L87),
changelog `2026-08-01-06-add-booking-version.xml`. Reinforced at the API level by
`RefundAlreadyPendingException`,
[RefundAlreadyPendingException.java:9](../backend/src/main/java/com/ticketwave/payment/exception/RefundAlreadyPendingException.java#L9).

**Bookings racing over overlapping seats could deadlock.** → Seats are now locked
in a fixed ascending-id order, making a lock cycle impossible.
[BookingServiceImpl.java:129](../backend/src/main/java/com/ticketwave/booking/service/BookingServiceImpl.java#L129).

**A guessable JWT secret in source control.** → `JWT_SECRET` was given **no
default**; startup fails fast if unset, in every environment including local dev.
[application.yml:57-62](../backend/src/main/resources/application.yml#L57-L62). The
test profile was hardened the same way, so it can never silently fall through,
[application-test.yml:8](../backend/src/test/resources/application-test.yml#L8).

**Rate limiting was bypassable behind a reverse proxy.** The filter keys on
`getRemoteAddr()`, which behind a proxy is the proxy's address. →
`server.forward-headers-strategy: native`, which trusts `X-Forwarded-For` only
from a recognised internal-proxy peer, so a direct client cannot spoof it.
[application.yml:93](../backend/src/main/resources/application.yml#L93).

**Unescaped LIKE wildcards in user-supplied search input.** A `%` or `_` in a
query changed the query's meaning. → Centralised escaping in booking search and
in both specification classes.
[BookingServiceImpl.java:379](../backend/src/main/java/com/ticketwave/booking/service/BookingServiceImpl.java#L379).

**Migrations could run against a read replica.** → `spring.liquibase.url` pinned
to `${spring.datasource.url}`, bypassing the routing datasource entirely.
[application.yml:31-38](../backend/src/main/resources/application.yml#L31-L38).

**Seat-state information disclosure.** `releaseOwnHold` originally revealed
whether a seat was held by someone else. → It is now a **silent no-op** for a
non-holder, and idempotent.
[SeatHoldServiceImpl.java:81](../backend/src/main/java/com/ticketwave/catalog/service/SeatHoldServiceImpl.java#L81).
The same reasoning produced the "foreign-owned resource **404s, not 403s**" rule
across all operator-scoped lookups.

### 4.3 Medium

**Operator seat edits could clobber a customer's in-flight checkout.** → Row-lock
via `findByIdForUpdate`, plus explicit rejection of `BOOKED` and actively-`HELD`
seats.
[SeatManagementServiceImpl.java:73-84](../backend/src/main/java/com/ticketwave/catalog/service/SeatManagementServiceImpl.java#L73-L84).

**A ledger write failure turned a successful charge into a 500.** The customer
would see an error for a payment that had actually gone through. → The ledger
append is now caught and logged at `ERROR`, never propagated.
[PaymentServiceImpl.java:200-216](../backend/src/main/java/com/ticketwave/payment/service/PaymentServiceImpl.java#L200-L216).

**`LazyInitializationException` writing the ledger.** The passed-in `Payment`'s
associations were loaded in an already-closed transaction. → The booking is
re-fetched inside the ledger's own transaction.
[LedgerServiceImpl.java:42](../backend/src/main/java/com/ticketwave/ledger/service/LedgerServiceImpl.java#L42).

**Cancelling up front could leave a customer with neither trip nor money.** A
refund request originally cancelled the booking immediately, freeing seats for
resale — so a later rejection was unrecoverable. → The booking now stays
`CONFIRMED` and keeps its seats until approval.
[RefundServiceImpl.java:161-165](../backend/src/main/java/com/ticketwave/payment/service/RefundServiceImpl.java#L161-L165).

**Misconfigured pricing could drive a fare to zero or negative.** → A defensive
`MIN_DEMAND_MULTIPLIER` floor of `0.10`.
[PricingServiceImpl.java:32](../backend/src/main/java/com/ticketwave/pricing/service/PricingServiceImpl.java#L32).

**PNR uniqueness relied on a pre-check.** → The DB `UNIQUE` constraint is now the
source of truth; a collision retries rather than double-issuing.
[PnrGeneratorImpl.java:14](../backend/src/main/java/com/ticketwave/booking/service/PnrGeneratorImpl.java#L14).

### 4.4 Low

- **Missing index** on `bookings.status` — status-only queries scanned the table.
  → `2026-08-02-07-add-bookings-status-index.xml`.
- **Missing audit-log search indexes** → `2026-08-02-02-add-audit-log-search-indexes.xml`.
- **Uncached schedule catalog** on the hottest read path → Caffeine cache with a
  30s TTL, deliberately excluding real-time seat availability.

---

## 5. How corrections were verified

Findings were not marked resolved on assertion. Three mechanisms carry the proof:

1. **Regression tests.** Per [`CLAUDE.md`](../CLAUDE.md), every bug fix ships with
   a test reproducing the original failure. Several name the bug in a comment —
   e.g. the 3DS detached-entity case at
   [PaymentServiceImplTest.java:377](../backend/src/test/java/com/ticketwave/payment/service/PaymentServiceImplTest.java#L377).
2. **Concurrency tests against a real database.** `SeatHoldConcurrencyIT` and
   `BookingFlowIT`'s opposite-lock-order test exercise the deadlock and
   double-booking findings under actual contention against a real PostgreSQL —
   impossible to verify with mocks or an in-memory DB.
3. **Build-enforced gates.** See §6.

Reproducing any of it is `cd backend && ./mvnw clean verify`. Every CI run also
publishes the merged JaCoCo report as an artifact, so the numbers in §7 can be
checked against a specific commit rather than taken on trust.

> An earlier version of this section pointed at `backend/audit-verify.log` as
> the record of a full-suite run. That file is matched by `*.log` in
> `backend/.gitignore` and was never committed, so the reference resolved to
> nothing for anyone but its author. Removed rather than committed: a log
> pasted into the repo goes stale the moment the next commit lands, which is
> exactly what had happened to it.

---

## 6. What now prevents regression

Review findings were converted into **automated gates** wherever possible, so the
same class of defect fails the build rather than waiting for the next review:

| Gate | Prevents |
|---|---|
| JaCoCo ≥80% line **and** branch, at `verify`, on merged unit+IT data | Untested code merging |
| JaCoCo 100% line+branch on `PricingServiceImpl`, `SeatHoldServiceImpl`, `SeatHoldExpirationScheduler`, `RefundPolicyService` | Silent financial bugs in the modules where they cost money |
| Failsafe bound to `integration-test`/`verify` | Integration tests silently not running |
| Unique-per-run IT fixtures (`AbstractIntegrationTest#uniqueSuffix`) | Two tests in one run colliding on the same fixture identifier |
| GitHub Actions running the backend, frontend **and** Playwright e2e suites on every push/PR | Every gate above depending on someone remembering to run it |
| `ddl-auto: validate` | Entity↔migration schema drift |
| `JWT_SECRET` with no default | Shipping a guessable signing key |
| Real PostgreSQL instead of H2 | Tests passing on behaviour PostgreSQL does not have |
| Testcontainers, one throwaway database per run | Rows surviving between runs, concurrent runs interfering — and any possibility of a test reaching a real database |
| Single `@RestControllerAdvice` | Stack traces reaching clients |

---

## 7. Most recent review round

A further review pass was run on **2026-08-03**, after the phases above. Its
findings, for completeness:

| Finding | Severity | Status |
|---|---|---|
| **`mvn verify` could only ever succeed once.** Every `*IT` fixture used hardcoded usernames/payment references against a shared database with no rollback, so the second run died on `uq_users_username`. `RefundFlowIT` lost all 8 tests at setup | **Critical** | Fixed — see below |
| Branch coverage was **74.28%** — below the 80% standard, and **no `BRANCH` limit was configured**, so nothing enforced it | High | Fixed: branch rule added; 92.41% branch / 97.59% line at the time, 98.05% / 99.34% after the follow-up round in §8 |
| JaCoCo `report`/`check` bound to the `test` phase ran **before** `integration-test`, so the 7 `*IT` classes contributed nothing to the gate they were meant to satisfy | Medium | Fixed: `prepare-agent-integration` + `merge` + `check` at `verify` |
| Docs (incl. `CLAUDE.md`) claimed integration tests used **Testcontainers**. Nothing does — the deps were declared but unused, and the ITs run against a local PostgreSQL | Medium | Fixed: docs corrected to reality, dead deps removed |
| `TenantScope` and `AuditLogSpecifications` had **zero** branch coverage — every caller mocked them | Medium | Fixed: unit tests added |
| A stale `target/jacoco.exec` inflates coverage, because JaCoCo appends by default | Low | Documented; `clean` required |
| `npm test` runs in watch mode and never exits — unusable in CI | Low | Documented: `npx ng test --watch=false` |
| `frontend/run-frontend.log` was tracked in git | Low | Fixed: untracked and ignored |
| **No CI pipeline** — every gate in §6 depended on someone remembering to run `mvn verify`, which is how a never-executing integration suite went unnoticed | High | Fixed: `.github/workflows/ci.yml` |
| 27 of 75 `file:line` citations in [`functional-test-cases.md`](functional-test-cases.md) had drifted | Low | Fixed, plus a verification script added to the doc |
| `README.md` claimed 70 endpoints; actual is 69 | Low | Fixed |
| `partner_webhooks.secret` is stored in plaintext — unavoidable, since it is the HMAC signing key, but unlike the BCrypt-hashed credential secret it is recoverable from the database | Informational | Documented in [`data-model.md`](data-model.md) |

---

### The re-runnability bug, in detail

Worth recording, because it is the same *shape* as the critical findings in §4.1:
silent, and invisible to every test that was passing.

The `*IT` suite ran against a shared local PostgreSQL with no Testcontainers
isolation and — deliberately, for concurrency reasons — no transactional
rollback. Fixtures nonetheless used fixed identifiers (`operator-refund-full`,
`PAY-HAPPY-1`, `support-processor`, …). Against a clean database that passes.
Against a database that has *already run the suite once*, it cannot.

Nobody noticed because the suite had never actually run: Failsafe was not wired
into the build (§4.1), so `mvn verify` skipped every `*IT` silently. Fixing that
finding is what exposed this one.

The fix was applied twice, and the second round is the interesting one. Round one
put the unique suffix at the **call site**, which cut failures from 8 to 4 — the
four support-user fixtures created directly in the test bodies were missed. Round
two moved suffix generation **inside the `newUser` helper**, where a caller
cannot forget it. Verified by running `mvn clean verify` twice consecutively
against the same database: both `BUILD SUCCESS`, 28 ITs each.

---

### Later the same day: making the gates actually run

Committing the round above turned out to be the round's last finding. The CI
pipeline it added had never executed, and every defect below surfaced only once
it did — each of them invisible on a developer machine, and each the same
*shape* as §4.1: silent, and passing every test that existed.

| Finding | Severity | Status |
|---|---|---|
| **The build could not run on a clean machine at all.** Any build against an empty local repository died in Maven's resolver with `BasicAuthCache cannot be cast to AuthCache` — two ClassRealms, before a single goal executed. A warm `~/.m2` never opens a transport, so every developer was fine and the first CI run was not | **Critical** | Fixed: `-Dmaven.resolver.transport=wagon` in `backend/.mvn/maven.config`, verified by building against a deliberately empty repository with and without it |
| **The e2e readiness probe poisoned the cache it was waiting on.** The workflow polled `GET /api/schedules/1` while the database was still unseeded; `ScheduleCatalogCache` stored that miss under key 1 for 30s; `global-setup.ts` then `TRUNCATE … RESTART IDENTITY`d, making the seeded schedule id 1. The search found the id, got the stale empty back, and answered 200 with an empty list | **High** | Fixed: probe `/v3/api-docs`, which touches no schedule data and is outside `/api/*` |
| `backend/.gitignore` excluded `maven-wrapper.properties`, leaving the newly added wrapper unable to resolve a distribution | Medium | Fixed |
| No `.gitattributes`, so `mvnw` could reach a Linux runner with CRLF and die on its shebang | Medium | Fixed: `mvnw` pinned to LF, `mvnw.cmd` to CRLF, `mvnw` committed mode 100755 |
| Testcontainers' image pull went out as Docker API 1.32 while the negotiated connection was 1.55; Docker Engine 26+ rejects it | Medium | Fixed: `api.version=1.44` as a Failsafe system property (docker-java's own key, *not* `DOCKER_API_VERSION`) |

Two hypotheses were pursued, disproved by experiment, and are recorded so they
are not retried: the Maven failure is **not** caused by the runner's Maven
version (3.9.16 reproduces it), and **not** by
`spring-cloud-contract-maven-plugin`'s extension realm (removing it leaves the
failure identical). The `<extensions>true</extensions>` removal was kept anyway,
on its own merits.

The method that finally worked was to stop guessing and make the failure
describe itself: the golden-path test now waits on `GET /api/search` and names
each failure mode separately, and the workflow prints the seeded rows and both
server logs into the job log rather than into an artifact nobody downloads.

---

## 8. Open items

- **`-Dmaven.resolver.transport=wagon` mitigates a third-party defect we cannot
  fix from here.** Root cause is now known:
  `spring-cloud-contract-maven-plugin` 5.0.3's plugin realm bundles
  `maven-resolver-transport-http:1.9.27` and `httpclient:4.5.14`, so once it has
  executed, its `HttpTransporterFactory` shadows Maven's own for the next plugin
  realm that needs a download. Removing it means excluding the bundled transport
  from the plugin's classpath, which risks its stub-downloading path for no gain
  in a project that generates stubs rather than fetching them. Revisit if the
  plugin stops shipping a resolver transport.
- **`docker.api.version` defaults to 1.44** for the Testcontainers JVM, and is
  overridable on the command line. It can go away once docker-java stops sending
  API 1.32 on image pulls.

### Closed since this document was written

Resolved on **2026-08-03**:

| Was open | Now |
|---|---|
| **Playwright e2e not in CI** — needed backend + frontend + `ticketwave_e2e` started by hand | Fixed: a third `e2e` job in [`ci.yml`](../.github/workflows/ci.yml) builds the jar, boots both servers against their own service container, and uploads traces plus both server logs on failure. `global-setup.ts` now reads its connection from the environment |
| **31 uncovered branches** | Down to **9**, and all 9 are now deliberate — see the Known gaps table in [`testing.md`](testing.md). Branch coverage 93.28% → **98.05%**, line 98.16% → **99.34%**, via 24 targeted tests |
| **No `docker-compose.yml`** | Added, with `docker/init-databases.sql`. Now scoped to *running the app* — the test suite no longer needs it |
| **ITs share one database** — concurrent runs could interfere, rows accumulated, and a forgotten env var could in principle point a run at real data | Fixed: Testcontainers, one throwaway `postgres:16` per test JVM ([`PostgresContainerSupport`](../backend/src/test/java/com/ticketwave/PostgresContainerSupport.java)). There is no longer any configured database URL for a stray env var to resolve to. Docker is now required by `mvn verify`, which is the price and is stated up front in [`testing.md`](testing.md) |

The three branches that remain uncovered on purpose are each marked with a
"note for coverage readers" comment at the site itself, rather than only in the
docs — the same *record-the-defect-next-to-the-code* habit that made §4
reconstructible at all.

---

## 9. Key learnings

What made this review method productive, in rough order of impact:

1. **Write the standard before the code.** `CLAUDE.md` existed from Phase 0, so
   every review graded against a fixed rubric instead of a shifting opinion.
2. **Split "find" from "fix".** Ranking findings *before* applying any of them
   keeps the list honest — a combined prompt tends to fix the easy items and
   quietly drop the rest.
3. **Review along two orthogonal axes.** Per-user-story catches missing
   behaviour; per-layer catches structural and build defects. The Failsafe bug
   was invisible to every user-story audit.
4. **Force a verdict vocabulary.** `PASS / FAIL / PARTIAL`,
   `MATCH / MISMATCH / MISSING` — a forced enum prevents a review from resolving
   into agreeable prose.
5. **Review more than once, at different maturity points.** Concurrency defects
   are invisible early; integration defects are invisible until a client exists.
6. **Convert findings into gates.** A finding fixed once can regress. A finding
   converted into a build rule cannot.
7. **Record the defect next to the code.** The comments naming each bug are what
   made this document reconstructible at all — and are why the rate-limit filter
   did not repeat the authentication-filter mistake.
8. **A gate that has never executed is indistinguishable from one that does not
   exist.** Every finding in "Later the same day" was produced by the act of
   running CI for the first time, not by reading the code. The same is true one
   level down: three of those defects were invisible because a developer machine
   has a warm `~/.m2`, a database that already exists, and a fast local server —
   none of which a clean runner has.
9. **When a symptom is ambiguous, fix the diagnostic before fixing the bug.**
   The e2e failure was chased through three refuted hypotheses on the strength
   of an error message (`element(s) not found`) that could not distinguish the
   two causes it had. Making the test name its own failure mode, and printing
   evidence into the job log rather than an artifact, found the real cause on
   the next run.
10. **Record refuted hypotheses, not just conclusions.** Two plausible
    explanations for the Maven failure were tested and eliminated. Without
    writing that down, the next person pays for the same experiments — and the
    commit that acted on one of them says, in its own message, that it was
    wrong.
