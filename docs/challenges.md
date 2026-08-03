# TicketWave — Challenges, and How They Were Resolved

The problems that actually cost time while building this system, what each one
turned out to be, and how it was proven fixed.

---

## A note on provenance

Two different kinds of evidence sit in this document, and they are not equally
strong.

**Sections 2.1 – 2.4** are reconstructed from the codebase and from
[`code-review.md`](code-review.md). The review sessions themselves were
interactive and their transcripts were not kept; what survives is the code, the
comments that name the defect they exist to prevent, and the prompt log in
`prompts`. Every claim there carries a `file:line` citation so it can be checked
rather than believed.

**Section 2.5** is first-hand and verifiable against git history — it happened
on **2026-08-03**, and each item names the commit that fixed it.

Where a fix was *asserted* rather than *demonstrated*, this document says so.

---

## 1. The one shape almost every hard problem had

Nearly every expensive problem in this project shared a property: **it produced
no error.** No exception, no failing test, no log line. The system simply did
the wrong thing, or claimed a guarantee it was not delivering.

That is why the hard part was rarely the fix. In almost every case below, the
fix is a handful of lines. The hard part was **finding out there was anything to
fix**, and the recurring technique that worked was to stop trusting that a green
build meant a working system.

---

## 2. The challenges

### 2.1 Code that ran but did nothing

**Authentication was never applied.** `JwtAuthenticationFilter` is a
`@Component` *and* a `jakarta.servlet.Filter`, so Spring Boot auto-registered it
globally on top of the intentional `addFilterBefore` wiring. The early copy ran
outside the security chain, its authentication was discarded, and
`OncePerRequestFilter`'s own guard then skipped the correct pass. Net effect:
authentication silently never happened.
→ Disabled the redundant registration,
[SecurityConfig.java:70](../backend/src/main/java/com/ticketwave/config/SecurityConfig.java#L70).

The interesting part is what came after. The same trap was **avoided by design**
in the rate limiter: `RateLimitingFilter` is deliberately *not* a `@Component`
and is wired through exactly one `FilterRegistrationBean`, with a comment naming
the earlier bug,
[RateLimitingFilter.java:26](../backend/src/main/java/com/ticketwave/ratelimit/RateLimitingFilter.java#L26).
A finding generalised into a rule is worth more than a finding patched once.

**3DS confirmation returned success and persisted nothing.** With
`open-in-view: false` the `Payment` was already detached when `confirmThreeDs`
mutated it, so the setters were inert. The API answered `SUCCEEDED` while the
row stayed `PENDING_3DS` forever.
→ Explicit re-`save`, plus a regression test that exists specifically to catch
it, [PaymentServiceImpl.java:179-183](../backend/src/main/java/com/ticketwave/payment/service/PaymentServiceImpl.java#L179-L183).

**A missing `@Primary` would have disabled the entire JPA layer.** With
`primaryDataSource`, `replicaDataSource` and the routing bean all typed
`DataSource`, Spring Boot's `@ConditionalOnSingleCandidate(DataSource.class)`
cannot resolve, `entityManagerFactory` is never created, and startup fails with
every repository reporting a missing bean — an error that never mentions the
class responsible,
[DataSourceRoutingConfig.java:62-76](../backend/src/main/java/com/ticketwave/config/DataSourceRoutingConfig.java#L62-L76).

### 2.2 Guarantees the tests were not actually providing

This was the most uncomfortable class of problem: the test suite was green, and
the green meant less than it appeared to.

**The integration tests had never run.** No Failsafe execution was bound, and
Surefire's default patterns do not match `*IT`. `mvn verify` silently skipped
every integration test while reporting success.
→ Failsafe wired to `integration-test` + `verify`,
[pom.xml](../backend/pom.xml).

**Fixing that immediately exposed the next one.** The `*IT` fixtures used
hardcoded identifiers (`operator-refund-full`, `PAY-HAPPY-1`, …) against a
shared database with no rollback. Against a clean database that passes; against
one that has already run the suite once, it cannot. `RefundFlowIT` lost all 8
tests at setup.

The fix is worth recording because the *first* attempt was wrong: putting the
unique suffix at each **call site** cut failures from 8 to 4, missing four
fixtures created inline in test bodies. Moving generation **inside the `newUser`
helper**, where a caller cannot forget it, took it to 0. Verified by running
`mvn clean verify` twice in a row against the same database.

**The coverage gate was grading the wrong thing.** Three separate faults at
once: JaCoCo's `check` was bound to `test`, so it ran *before* the integration
tests and graded Surefire alone; no `BRANCH` limit was configured, so the branch
number was measured and ignored; and generated code (MapStruct `*MapperImpl`)
plus a dev-only seeder accounted for roughly two thirds of all missed branches,
burying the real gaps.
→ Separate exec files per run, merged, with `check` moved to `verify` and a
branch limit added. Branch coverage went 74.28% → 92.41% → **98.05%**, the last
step via 24 targeted tests. The remaining 9 uncovered branches are deliberate
and each carries a "note for coverage readers" comment at the site itself —
see [`testing.md`](testing.md).

### 2.3 Concurrency

These could not be found by reading, and could not be verified with mocks.

| Problem | Resolution |
|---|---|
| Two refund requests could both read `CONFIRMED` and both succeed | `@Version` optimistic lock on `Booking`, mapped to `409 CONCURRENT_UPDATE`, [Booking.java:77-87](../backend/src/main/java/com/ticketwave/booking/entity/Booking.java#L77-L87) |
| Bookings racing over overlapping seats could deadlock on each other's locks | Seats locked in fixed ascending-id order, making a lock cycle impossible, [BookingServiceImpl.java:129](../backend/src/main/java/com/ticketwave/booking/service/BookingServiceImpl.java#L129) |
| An operator editing a seat could clobber a customer's in-flight checkout | Row lock via `findByIdForUpdate`, plus explicit rejection of `BOOKED` and actively-`HELD` seats, [SeatManagementServiceImpl.java:73-84](../backend/src/main/java/com/ticketwave/catalog/service/SeatManagementServiceImpl.java#L73-L84) |

The resolution that mattered as much as the fixes: these are verified by
`SeatHoldConcurrencyIT` and `BookingFlowIT`'s opposite-lock-order test, against
a **real PostgreSQL**. `SELECT … FOR UPDATE` semantics and PostgreSQL's
abort-the-whole-transaction behaviour on constraint violations are exactly what
those tests exist to exercise — an H2-backed test would have passed while the
production path stayed broken.

### 2.4 Money, and the cost of being wrong about it

**Requesting a refund could leave a customer with neither trip nor money.** The
original flow cancelled the booking immediately on request, freeing its seats
for resale — so a later rejection had nothing to give back.
→ The booking now stays `CONFIRMED` and keeps its seats until approval,
[RefundServiceImpl.java:161-165](../backend/src/main/java/com/ticketwave/payment/service/RefundServiceImpl.java#L161-L165).

**A ledger write failure turned a successful charge into a 500**, showing the
customer an error for money that had actually moved.
→ The ledger append is caught and logged at `ERROR`, never propagated,
[PaymentServiceImpl.java:200-216](../backend/src/main/java/com/ticketwave/payment/service/PaymentServiceImpl.java#L200-L216).

**Misconfigured pricing could drive a fare to zero or negative.**
→ A defensive `MIN_DEMAND_MULTIPLIER` floor,
[PricingServiceImpl.java:32](../backend/src/main/java/com/ticketwave/pricing/service/PricingServiceImpl.java#L32).

These modules are the reason the build enforces **100% line and branch
coverage** on `PricingServiceImpl`, `SeatHoldServiceImpl`,
`SeatHoldExpirationScheduler` and `RefundPolicyService` specifically, rather
than trusting the 80% bundle-wide bar.

### 2.5 The build and CI (2026-08-03)

The hardest sequence in the project, and the one worth reading if you only read
one section. Every defect here was **invisible on a developer machine** and
appeared the moment CI ran for the first time.

**The build could not run on a clean machine at all.** Any build against an
empty local repository died in Maven's resolver:

```
ClassCastException: org.apache.http.impl.client.BasicAuthCache
cannot be cast to org.apache.http.client.AuthCache
  at org.eclipse.aether.transport.http.HttpTransporter.<init>
  at org.apache.maven.plugin.internal.DefaultMavenPluginManager.createPluginRealm
```

Root cause, established from a `-X` run against an empty repository:
`spring-cloud-contract-maven-plugin` 5.0.3's plugin realm bundles its own
`maven-resolver-transport-http:1.9.27` and `httpclient:4.5.14`. From the first
time that plugin executes, its `HttpTransporterFactory` is a candidate
component; the next plugin realm Maven builds needs a download, gets that
factory, and constructs `HttpTransporter` inside the plugin's realm while the
`AuthCache` interface it casts to still comes from Maven core.

It reproduces only against a cold repository — a warm `~/.m2` needs no download
after the plugin has run, so no transport is ever opened. That is precisely why
every developer machine was fine and the very first CI run was not.
→ `-Dmaven.resolver.transport=wagon` in
[`.mvn/maven.config`](../backend/.mvn/maven.config), with the root cause written
next to it (`9dbd8db`, root cause named in `3ee2fff`).

**The e2e readiness probe poisoned the cache it was waiting on.** The workflow
polled `GET /api/schedules/1` while the database was still unseeded;
`ScheduleCatalogCache` stored that miss under key `1` for 30s; Playwright's
`global-setup.ts` then `TRUNCATE … RESTART IDENTITY`d, which made the seeded
schedule id `1`. The search found the id, got the stale empty back, and answered
`200` with an empty list. The golden-path test failed on a cache entry the
readiness check itself had planted — and only when the whole sequence fit inside
the 30s TTL, which is why it survived three local runs and failed in CI.
→ Probe `/v3/api-docs`, which touches no schedule data and sits outside
`/api/*` (`e81eae7`).

**Testcontainers could not talk to Docker.** Testcontainers negotiates the
connection correctly — it logs `API Version: 1.55` — but its image-pull command
still goes out as client version 1.32, which Docker Engine 26+ rejects. The
symptom moves between the pull and the `/info` probe depending on whether
`~/.testcontainers.properties` has cached a client strategy, which made one
fault look like several unrelated ones.
→ `docker.api.version` (default 1.44) passed to the Failsafe JVM. Note it is
docker-java's own `api.version` key, **not** the `DOCKER_API_VERSION`
environment variable, which is namespaced differently and does not help — that
was tried twice (`90d7adb`, made configurable in `3ee2fff`).

**Two findings had been "resolved" by writing them down.** A stale
`target/jacoco.exec` inflated coverage (mitigation on record: "remember to run
`clean`"), and `npm test` ran in watch mode and never exited (mitigation on
record: "type `npx ng test --watch=false` instead"). Neither prevented anything.
→ `<append>false</append>` on the JaCoCo agent, and `npm test` inverted so the
obvious command is the safe one (`c9df2d9`). Verified rather than asserted: a
`clean verify` followed by a `verify` with no clean now produces an identical
report.

Two smaller ones from the same day, both of which would have broken the build
silently: `backend/.gitignore` excluded `maven-wrapper.properties`, leaving the
wrapper unable to resolve a distribution; and with no `.gitattributes`, `mvnw`
could reach a Linux runner with CRLF and die on its shebang.

### 2.6 The meta-challenge: a developer machine lies

Section 2.5 is really one problem wearing four costumes. A developer machine has
a warm `~/.m2`, a database that already exists, images already pulled, and a
fast local server. A clean CI runner has none of those. **Every single build
failure that day came from something the local environment was quietly
providing.**

The general resolution was to stop treating "works here" as evidence, and to
make CI the thing that decides. The corollary is uncomfortable: the CI pipeline
was itself added late, and adding it is what surfaced all of this.

---

## 3. What actually worked as a method

Three habits did most of the work. All three were learned the expensive way,
during the sequence in §2.5.

**1. Reproduce before fixing.** The Maven failure was "fixed" once on a
hypothesis (the runner's Maven version) that a proper reproduction later
disproved — the commit message for `da9a202` says so in its own text. Nothing
was reliable until there was a local reproduction: an empty repository, the real
goals, one variable changed at a time. From that point the fix took one attempt.

**2. When the symptom is ambiguous, fix the diagnostic first.** The e2e failure
was chased through three refuted hypotheses on the strength of one error message
(`element(s) not found`) that could not distinguish the two causes it had. The
turning point was making the test *name its own failure mode* — waiting on
`GET /api/search` so "no request was sent", "the request was rejected" and "the
request returned nothing" became three different messages — and printing the
seeded rows into the job log instead of an artifact nobody downloads. The real
cause showed up on the next run.

**3. Record what you disproved, not just what you concluded.** Refuted
hypotheses are written into the files they concern, so the next person does not
re-run the same experiments.

---

## 4. Hypotheses that were tested and turned out to be wrong

Kept deliberately. Each cost real time; none should cost it twice.

| Hypothesis | How it was killed |
|---|---|
| The Maven failure is caused by the runner's older Maven | Pinned 3.9.16 via the wrapper; it reproduces identically |
| The Maven failure is caused by the contract plugin's `<extensions>true</extensions>` | Removed it; a cold build fails unchanged. The plugin *is* the culprit, but through its execution realm, not its extension realm |
| The e2e failure is a date/timezone mismatch | The spec never fills a date and the form defaults to empty |
| The e2e failure is rate limiting | Suite passes 5/5 with the limit set to 15/min, a quarter of CI's |
| The e2e failure is the freshly-created database | Dropped and recreated it so Liquibase built the schema from nothing; passes 5/5 |
| Testcontainers cannot work on this machine | Wrong. The diagnosis had been built on a log filtered so aggressively it hid the line that mattered |

The last row is the one worth internalising: **the investigation was wrong
because the evidence had been narrowed before it was read.**

---

## 5. Still open

| Item | Why it stays |
|---|---|
| `-Dmaven.resolver.transport=wagon` | Mitigates a third-party packaging defect. Removing the cause means excluding the bundled transport from the plugin's classpath, risking its stub-downloading path for no gain here |
| `docker.api.version` pinned to 1.44 | Goes away once docker-java stops sending API 1.32 on image pulls |
| `partner_webhooks.secret` stored in plaintext | Inherent: it is the HMAC signing key and must be recoverable to sign with. Documented in [`data-model.md`](data-model.md) |

None is blocking; all three are constraints rather than debts.

---

## 6. If there is one thing to take from this

The fixes in this document are almost all small. The work was in the finding,
and what made finding possible was, in order: a real database instead of an
in-memory one, integration tests that actually execute, a CI runner that does
not share the developer's conveniences, and a diagnostic that says which of the
possible causes occurred.

Every one of those was itself added *after* something silent had already gone
wrong.
