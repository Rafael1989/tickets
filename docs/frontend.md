# TicketWave — Frontend

The Angular 22 single-page app: how it is organised, how it authenticates, how
errors and state are handled, and what each feature screen does.

Backend counterpart: [`architecture.md`](architecture.md) and
[`functional-specification.md`](functional-specification.md).

---

## 1. Stack and conventions

| | |
|---|---|
| Framework | Angular 22, standalone components (no NgModules) |
| Reactivity | Signals (`signal`, `computed`, `effect`) for state; RxJS for HTTP |
| HTTP | `provideHttpClient(withInterceptors([...]))` — functional interceptors |
| Routing | `provideRouter` with lazy `loadComponent` on every feature route |
| Tests | Vitest (unit/component) + Playwright (e2e) |

Naming rules:

- One concern per file — `feature-name.component.ts` / `.html` / `.scss`,
  `feature-name.service.ts`, `feature-name.model.ts`, `feature-name.guard.ts`.
- Component selectors are prefixed **`tw-`** (`tw-seat-map`, `tw-countdown`).
- Services that call the backend are named after the **resource**, not the verb:
  `booking.service.ts`, never `get-booking.service.ts`.

```
src/app/
├── core/            cross-cutting: guards, interceptors, models, services, validators
├── features/        one folder per user-facing area, lazy-loaded
└── shared/          reusable presentational components
```

---

## 2. Routing and access control

`app.routes.ts` — every feature route lazy-loads its component.

| Path | Guard | Screen |
|---|---|---|
| `/` | `homeGuard` | Redirects to the role's landing screen |
| `/login`, `/register` | — | Authentication |
| `/search` | — | Public schedule search |
| `/schedules/:id` | — | Seat selection (richer when signed in) |
| `/find-booking` | — | Guest PNR + email lookup |
| `/checkout` | `authGuard` | Passengers, promo, payment |
| `/account` | `authGuard` | Profile, preferences, saved passengers |
| `/bookings`, `/bookings/:id` | `authGuard` | My bookings, booking detail |
| `/operator` | `roleGuard` `OPERATOR` | Operator portal |
| `/support` | `roleGuard` `SUPPORT` | Support panel |
| `/admin` | `roleGuard` `ADMIN` | Admin panel |
| `**` | `homeGuard` | Unmatched → role landing screen |

### The three guards

- **`authGuard`** — allows when authenticated; otherwise redirects to `/login`
  carrying `redirectTo` so the attempted URL survives the round trip.
- **`roleGuard`** — route-data driven (`data: { role: 'OPERATOR' }`) so one
  factory covers every gated section. Unauthenticated → `/login`; authenticated
  but missing the role → `/search`.
- **`homeGuard`** — always returns a `UrlTree`, so it redirects rather than ever
  activating. Resolves the landing screen from `AuthService.homePath()`.

`homePath()` is ordered **most-privileged first** (`ADMIN` → `SUPPORT` →
`OPERATOR` → `/search`) so a multi-role token resolves deterministically. Staff
roles have no business on the customer search/booking screens, so a login, a logo
click, or a bare `/` must never drop them there.

> Guards are UX, not security. Every one of these routes is also enforced
> server-side by `@PreAuthorize` on the service method. A tampered token or a
> hand-crafted request gets a 401/403 from the backend regardless of what the
> SPA allows.

---

## 3. Authentication

`AuthService` (`core/services/auth.service.ts`) holds the JWT in a signal, backed
by `localStorage` under `tw.accessToken`, and decodes the payload client-side to
derive:

| Computed | Meaning |
|---|---|
| `payload` | Decoded JWT claims, or null if malformed |
| `isAuthenticated` | A decodable token is present |
| `username` | `sub` claim |
| `roles` | `roles` claim |
| `homePath` | Landing route for the signed-in role |
| `isCustomer` | Authenticated **and** `homePath` is `/search` |

Decoding is defensive: a token without three segments, or one that fails
base64/JSON parsing, yields `null` rather than throwing — a corrupted
`localStorage` entry logs the user out instead of breaking the app shell.

---

## 4. Interceptors

Two functional interceptors, registered in `app.config.ts` in this order:

**`authInterceptor`** — attaches `Authorization: Bearer <token>` when a token
exists; otherwise passes the request through untouched.

**`errorInterceptor`** — the single place HTTP errors are handled:

| Status | Behaviour |
|---|---|
| `401` | Log out, redirect to `/login`, toast "Your session has expired." |
| `≥ 400` | Toast the backend's `message` from the standard error body, falling back to a generic message |

It re-throws after handling, so a component that genuinely needs to react to a
specific failure still can — but **components do not implement their own HTTP
error branching**. They subscribe to already-normalised results from the service
layer.

---

## 5. State

There is no NgRx or global store. State is deliberately kept in three narrow
places:

| Holder | Scope | Lifetime |
|---|---|---|
| `AuthService` | Token and derived identity | `localStorage`, survives refresh |
| `BookingDraftService` | Schedule + selected seats + promo, carried from seat selection to checkout | **In-memory only**, lost on refresh — checkout is one continuous flow, not a resumable draft |
| `RescheduleContextService` | The booking being rescheduled, carried into the search/seat-selection flow | In-memory |
| `NotificationService` | Toast queue, auto-dismiss after 5s | In-memory |

Everything else is component-local signals fed by a resource service.

---

## 6. Core services

One per backend resource, each a thin typed `HttpClient` wrapper:

`auth` · `user` · `preferences` · `passenger` · `search` · `schedule` ·
`booking` · `booking-draft` · `payment` · `refund` · `reschedule-context` ·
`route` · `vehicle` · `driver` · `fare-rule` · `inventory-management` ·
`operator-report` · `promo` · `partner` · `admin-user` · `audit` ·
`notification`

Request/response shapes live in `core/models/*.model.ts`, mirroring the backend
DTOs.

**Validators** (`core/validators/`) — `luhn.validator.ts` (card number checksum,
so an obviously invalid PAN is caught before a round trip) and
`id-number.validator.ts` (passenger identity document).

---

## 7. Feature screens

### Customer

| Screen | Notes |
|---|---|
| **Search** | Type/origin/destination/venue/date/price/seat-class filters, all optional. Public |
| **Seat selection** | Live seat map with real per-seat fares; holds a seat on selection and shows the countdown inline |
| **Checkout** | Passenger assignment, promo code, payment. Guarded by the hold countdown |
| **My bookings** | Every status including cancelled, newest first |
| **Booking details** | E-ticket, QR code, reschedule and cancellation entry points |
| **Cancellation wizard** | Shows the policy quote (refundable vs non-refundable) before confirming |
| **Refund status tracker** | Follows a request through `PENDING` → `PROCESSED`/`REJECTED` |
| **Guest lookup** | PNR + email, no account required |
| **Account** | Profile info, preferences, saved passengers |

### Operator portal

Analytics dashboard (revenue/occupancy per route) · schedule manager · fleet
manager (vehicles, drivers) · seat grid editor · fare matrix (including bulk
fare-rule load).

### Support panel

Booking lookup by PNR or customer, and refund approval/rejection with the
optional fee waiver.

### Admin panel

Users and roles · partners, API credentials and webhooks · promo codes · audit
log search · ledger reconciliation.

---

## 8. Shared components

| Selector | Purpose |
|---|---|
| `tw-countdown` | Ticks to an ISO timestamp (a seat hold's `heldUntil`). Emits `expired` **exactly once**; callers decide what that means. `bare` input drops the pill chrome for tight spaces. Turns urgent at ≤ 60s |
| `tw-e-ticket-card` | Printable ticket summary |
| `tw-qr-code` | PNR as a scannable code |
| `tw-navbar` | Role-aware navigation |
| `tw-toast` | Renders the `NotificationService` queue |

The countdown deliberately owns only the clock — it does not block checkout or
navigate. That keeps it reusable between the seat map (per-seat pill) and the
checkout header (hold banner), which react to expiry differently.

---

## 9. Running and testing

```bash
cd frontend
npm install
npm start                   # ng serve → http://localhost:4201
npm test                    # Vitest, one shot; npm run test:watch to watch
npm run e2e                 # Playwright, needs backend + frontend running
```

`proxy.conf.json` forwards `/api` to `http://localhost:8081`, matching the
backend default — no extra configuration when both sides stay on their defaults.

57 spec files / **401 tests** cover services, guards, interceptors and
components — all passing. Playwright covers two end-to-end journeys
(`auth.spec.ts`, `booking-golden-path.spec.ts`). See [`testing.md`](testing.md)
for the full strategy.
