# Load tests

k6 scripts targeting TicketWave's highest-concurrency read/write paths, per
the "Load tests for search and seat selection" requirement in `genai.txt`.

## Prerequisites

- [k6](https://k6.io/docs/get-started/installation/) installed locally (not
  a project dependency — these scripts aren't run as part of `mvn test`).
- A running backend instance with seeded data — `DevDataSeeder` is
  `@Profile("seed")`, so it needs the profile named explicitly:

  ```bash
  cd backend
  JWT_SECRET=local-dev-secret-key-at-least-32-bytes-long \
  SERVER_PORT=8081 \
  mvn spring-boot:run -Dspring-boot.run.profiles=seed
  ```

  Any other environment works too, as long as it has at least one CUSTOMER
  account and some AVAILABLE seats. Without the seed, `customer1` does not
  exist and every login 401s — which surfaces as `http_req_failed` at 100%
  and `login succeeded` at 0%, not as an obvious "no seed data" message.

## Running

```bash
k6 run load-test/search-and-seat-hold.js

# Against a different environment / account:
k6 run \
  -e BASE_URL=https://staging.example.com \
  -e TEST_USERNAME=customer1 \
  -e TEST_PASSWORD='SeedPass123!' \
  load-test/search-and-seat-hold.js
```

## What it covers

- **`search_browsing`**: ramps up to 20 concurrent virtual users issuing
  `GET /api/search` with varied filters, for 2 minutes total.
- **`seat_hold_contention`**: ramps up to 10 concurrent virtual users that
  discover a real available seat via search, then hammer
  `POST .../seats/{id}/hold` (releasing immediately after) — the same
  pessimistic-lock code path `SeatHoldConcurrencyIT` covers at the
  integration-test level, exercised here under sustained load instead of a
  single burst.

## Reading the results

This script's "success" criteria are unusual — read the header comment in
`search-and-seat-hold.js` before interpreting a run. In short:

- **429 on search is expected**, not a failure: it's
  `com.ticketwave.ratelimit.RateLimitingFilter` doing its job (default 60
  requests/60s per IP). It shows up in the `search_rate_limited` custom
  metric, not `http_req_failed`.
- **409 on a seat hold is expected under contention**, not a failure: it's
  `SeatUnavailableException` from two VUs racing the same seat, with the
  pessimistic lock correctly letting only one win. It shows up in
  `hold_contended`.
- Only `hold_errored` (an unexpected status on a hold attempt) and the
  `http_req_failed`/`http_req_duration` thresholds in the script represent
  genuine regressions.

## Known limitations

- Not run in CI or verified end-to-end as part of this change — this
  sandbox has neither a k6 binary nor a running instance of the backend to
  execute it against. Run it locally against a dev instance before relying
  on its output.
- Assumes the default dev-seed credentials (`customer1` /
  `SeedPass123!` — see `DevDataSeeder.SEED_PASSWORD`); override via
  `TEST_USERNAME`/`TEST_PASSWORD` for any other environment.
