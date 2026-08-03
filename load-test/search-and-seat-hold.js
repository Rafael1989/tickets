/*
 * k6 load test for TicketWave's two highest-contention read/write paths:
 *   - GET /api/search            (public, rate-limited, no auth)
 *   - POST/DELETE .../seats/{id}/hold  (authenticated, pessimistic-locked)
 *
 * Usage:
 *   k6 run load-test/search-and-seat-hold.js
 *   k6 run -e BASE_URL=https://staging.example.com load-test/search-and-seat-hold.js
 *
 * Environment variables (all optional, defaults target a local dev-seeded
 * instance started with the "seed" profile, i.e.
 * `mvn spring-boot:run -Dspring-boot.run.profiles=seed`, which ships ~30
 * seeded customers - without it TEST_USERNAME does not exist and every login
 * here 401s):
 *   BASE_URL      default http://localhost:8081
 *   TEST_USERNAME default customer1  (must be a real, seeded CUSTOMER account)
 *   TEST_PASSWORD default SeedPass123!
 *
 * What "success" looks like here is NOT the same as a typical load test:
 *   - 429 on /api/search is the token-bucket rate limiter (see
 *     com.ticketwave.ratelimit) doing its job under load, not a failure.
 *     ticketwave.rate-limit.requests-per-window (default 60/60s) is almost
 *     certainly the first thing this script exercises — that's the point.
 *   - 409 on a seat hold is SeatUnavailableException: two VUs raced the same
 *     seat and the pessimistic lock (SeatRepository.findByIdForUpdate)
 *     correctly let only one of them win. See SeatHoldConcurrencyIT for the
 *     equivalent correctness assertion at the integration-test level; this
 *     script instead measures it under sustained concurrent load.
 * Read the custom metrics (search_rate_limited, hold_contended,
 * hold_succeeded) in the summary rather than relying on the default
 * http_req_failed rate, which would otherwise conflate "the system is
 * correctly rejecting this" with "the system is broken."
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const TEST_USERNAME = __ENV.TEST_USERNAME || 'customer1';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'SeedPass123!';

const searchRateLimited = new Counter('search_rate_limited');
const holdSucceeded = new Counter('hold_succeeded');
const holdContended = new Counter('hold_contended');
const holdErrored = new Counter('hold_errored');
const seatHoldDuration = new Trend('seat_hold_duration', true);

export const options = {
    scenarios: {
        search_browsing: {
            executor: 'ramping-vus',
            exec: 'searchBrowsing',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },
                { duration: '1m', target: 20 },
                { duration: '30s', target: 0 },
            ],
        },
        seat_hold_contention: {
            executor: 'ramping-vus',
            exec: 'seatHoldContention',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },
                { duration: '1m', target: 10 },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        // Anything other than 2xx/401/403/404/409/429 is a genuine bug, not
        // expected contention/rate-limiting — see the header comment.
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
    },
};

/*
 * `type` is bound to the RouteType enum by constant name, so it must be
 * uppercase. The lowercase values CodedEnum.getCode() returns ("flight") are
 * the persistence form and are rejected at the API boundary.
 */
const SEARCH_QUERIES = [
    {},
    { origin: 'NYC' },
    { destination: 'Boston' },
    { type: 'BUS' },
    { type: 'FLIGHT' },
];

/**
 * Hand-rolled rather than URLSearchParams: k6 runs on goja, not Node or a
 * browser, and defines neither URLSearchParams nor URL. Using it throws
 * "ReferenceError: URLSearchParams is not defined" on every iteration - which
 * fails quietly, because the exception aborts the iteration before any request
 * is made, so the scenario reports millions of instant iterations and zero
 * search traffic instead of an obvious failure.
 */
function toQueryString(query) {
    return Object.keys(query)
        .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(query[key])}`)
        .join('&');
}

export function searchBrowsing() {
    const query = SEARCH_QUERIES[Math.floor(Math.random() * SEARCH_QUERIES.length)];
    const params = toQueryString(query);
    const res = http.get(`${BASE_URL}/api/search${params ? '?' + params : ''}`, {
        tags: { name: 'GET /api/search' },
    });

    if (res.status === 429) {
        searchRateLimited.add(1);
    } else {
        check(res, { 'search returns 200': (r) => r.status === 200 });
    }

    sleep(Math.random() * 1.5);
}

function login() {
    const res = http.post(
        `${BASE_URL}/api/login`,
        JSON.stringify({ username: TEST_USERNAME, password: TEST_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/login' } }
    );
    check(res, { 'login succeeded': (r) => r.status === 200 });
    if (res.status !== 200) {
        return null;
    }
    return res.json('accessToken');
}

/**
 * Discovers a real (scheduleId, seatId) pair from live search results
 * rather than hardcoding one — dev-seeded data is randomized per run, and a
 * hardcoded id would make this script silently stop testing anything real
 * as soon as the seed data changes.
 */
function findHoldableSeat() {
    const searchRes = http.get(`${BASE_URL}/api/search`, { tags: { name: 'GET /api/search (discovery)' } });
    if (searchRes.status !== 200) {
        return null;
    }
    const schedules = searchRes.json();
    if (!schedules || schedules.length === 0) {
        return null;
    }
    const schedule = schedules[Math.floor(Math.random() * schedules.length)];

    const seatsRes = http.get(`${BASE_URL}/api/schedules/${schedule.scheduleId}/seats`, {
        tags: { name: 'GET /api/schedules/{id}/seats (discovery)' },
    });
    if (seatsRes.status !== 200) {
        return null;
    }
    const seats = seatsRes.json().filter((s) => s.status === 'AVAILABLE');
    if (seats.length === 0) {
        return null;
    }
    const seat = seats[Math.floor(Math.random() * seats.length)];
    return { scheduleId: schedule.scheduleId, seatId: seat.id };
}

let cachedToken = null;

export function seatHoldContention() {
    if (!cachedToken) {
        cachedToken = login();
        if (!cachedToken) {
            sleep(1);
            return;
        }
    }
    const authHeaders = { headers: { Authorization: `Bearer ${cachedToken}` } };

    const target = findHoldableSeat();
    if (!target) {
        sleep(1);
        return;
    }

    const holdStart = Date.now();
    const holdRes = http.post(
        `${BASE_URL}/api/schedules/${target.scheduleId}/seats/${target.seatId}/hold`,
        null,
        { ...authHeaders, tags: { name: 'POST .../seats/{id}/hold' } }
    );
    seatHoldDuration.add(Date.now() - holdStart);

    if (holdRes.status === 200) {
        holdSucceeded.add(1);
        // Release immediately so this script doesn't permanently exhaust a
        // shared/dev environment's seat inventory over a long run.
        sleep(0.2 + Math.random() * 0.3);
        http.del(
            `${BASE_URL}/api/schedules/${target.scheduleId}/seats/${target.seatId}/hold`,
            null,
            { ...authHeaders, tags: { name: 'DELETE .../seats/{id}/hold' } }
        );
    } else if (holdRes.status === 409) {
        // Expected under contention — see the header comment.
        holdContended.add(1);
    } else {
        holdErrored.add(1);
        check(holdRes, { 'hold did not error unexpectedly': () => false });
    }

    sleep(Math.random());
}
