# TicketWave — Functional Specification

Derived directly from the `@Operation`/`@ApiResponses` annotations already present on all 20
`@RestController` classes (69 annotated endpoints total, including the 2 public auth endpoints
below) and from the `@PreAuthorize` rules enforced at the service layer per
[`CLAUDE.md`](../CLAUDE.md)'s API conventions. Every description below is condensed from the
source annotation, not invented — the live machine-readable version is always available at
`/v3/api-docs` (springdoc) while the app is running, and rendered at `/swagger-ui.html`.

Money fields are `BigDecimal`. Every error response follows the single shape enforced by
`GlobalExceptionHandler`:

```json
{ "status": 404, "error": "BOOKING_NOT_FOUND", "message": "...", "timestamp": "..." }
```

For the logic *behind* these endpoints see [`business-rules.md`](business-rules.md);
for how the layers fit together, [`architecture.md`](architecture.md); for the
persistence model, [`data-model.md`](data-model.md).

**Role legend:** `Public` — no token · `Customer` — any authenticated user, ownership-checked
where relevant · `Support/Admin` · `Operator` — scoped to routes/vehicles/drivers/schedules the
caller owns · `Admin` · `Partner API` — a short-lived `PARTNER_API` token issued by the OAuth2
token endpoint, not a human login.

---

## 1. Authentication — `AuthController` (`/api`)

| Method & path | Summary | Role |
|---|---|---|
| `POST /api/register` | Register a new account. Always creates a `CUSTOMER` — operator/support/admin accounts are provisioned via `POST /api/users` instead. | Public, rate-limited |
| `POST /api/login` | Authenticate and receive a JWT (`accessToken`, `tokenType`, `expiresInSeconds`). | Public, rate-limited |

## 2. Search & schedules — `SearchController`, `ScheduleController` (`/api/search`, `/api/schedules`)

| Method & path | Summary | Role |
|---|---|---|
| `GET /api/search` | Search schedules by type/origin/destination/venue/date/price/seat class. Every filter optional; origin/destination/venue match case-insensitively on any substring; an all-empty request matches every non-cancelled, not-yet-departed schedule. Each result carries a real-time available-seat count. | Public, rate-limited |
| `GET /api/schedules/{id}` | Get schedule details. | Public, rate-limited |
| `GET /api/schedules/{id}/seats` | Full seat map, including HELD/BOOKED seats, with each seat's real `estimatedFare`. `heldByMe` is only ever true for the seat's current holder. | Public (richer when authenticated), rate-limited |
| `POST /api/schedules/{scheduleId}/seats/{seatId}/hold` | Hold a seat for the caller for a configurable TTL; refreshed (not re-held) on every subsequent call by the same holder, including at booking creation. | Customer |
| `DELETE /api/schedules/{scheduleId}/seats/{seatId}/hold` | Release the caller's own hold. Idempotent no-op if not held by the caller — never reveals the seat's actual state to a non-holder. | Customer |

## 3. Bookings, payments & reschedule — `BookingController` (`/api/bookings`)

| Method & path | Summary | Role |
|---|---|---|
| `POST /api/bookings` | Create a booking and hold its seats (`INITIATED` status). | Customer |
| `PUT /api/bookings/{id}/confirm` | Compatibility no-op: succeeds only if the booking is already `CONFIRMED` (via a successful payment), 409 otherwise. Never changes state itself. | Customer (owner) |
| `POST /api/bookings/{id}/payments` | Record a payment; confirms the booking on success. **Idempotent on `reference`** — a replayed reference returns the original result instead of double-charging. | Customer (owner) |
| `POST /api/bookings/{id}/payments/{paymentId}/confirm-3ds` | Settle a `PENDING_3DS` payment's simulated challenge; a code mismatch fails it exactly like a decline. | Customer (owner) |
| `GET /api/bookings/me` | List the authenticated customer's own bookings, newest first. Every status (including `CANCELLED`), so a past or cancelled trip is still findable. | Customer |
| `GET /api/bookings/{id}` | Get a booking's details. | Customer (owner) |
| `GET /api/bookings/pnr/{pnr}` | Look up a booking by PNR. | Support/Admin |
| `GET /api/bookings/search?query=` | Search by exact PNR or a substring of customer email/passenger name. Max 25 results, newest first. | Support/Admin |
| `GET /api/bookings/pnr/{pnr}/lookup?email=` | Guest "find my booking" — PNR + the account email as a second factor, so a bare PNR guess can't retrieve someone else's itinerary. | Public, rate-limited |
| `GET /api/bookings/{id}/reschedule-quote` | Preview the fare-difference outcome of moving to a new schedule/seats, without committing. | Customer (owner) |
| `PUT /api/bookings/{id}/reschedule` | Change schedule/seats. Free for `INITIATED`; for `CONFIRMED`, applies the cancellation-proximity window then settles the fare difference (collects a top-up or issues a `RESCHEDULE_CREDIT`). | Customer (owner) |
| `GET /api/bookings/{id}/refund-quote` | Preview the cancellation-policy outcome without cancelling. | Customer (owner) |
| `POST /api/bookings/{id}/refunds` | Request a cancellation: applies the policy and creates a `PENDING` refund. The booking stays `CONFIRMED` and keeps its seats until support approves — 409 if a request is already awaiting review. | Customer (owner) |
| `GET /api/bookings/{id}/refunds` | List every refund raised against a booking. | Customer (owner) |

## 4. Refund settlement — `RefundController` (`/api/refunds`)

| Method & path | Summary | Role |
|---|---|---|
| `PUT /api/refunds/{id}/process` | Settle a `PENDING` refund. **Approving** refunds the payment *and* cancels the booking, releasing its seats; **rejecting** leaves the booking `CONFIRMED` and the payment untouched. Optionally waives the cancellation fee with a required reason (approval only). | Support/Admin |

## 5. The authenticated caller's own account — `UserController`, `PreferencesController`, `PassengerController`

| Method & path | Summary | Role |
|---|---|---|
| `GET /api/users/me` | Get the caller's own account. | Customer |
| `PUT /api/users/me/email` | Change the caller's own email. | Customer |
| `PUT /api/users/me/password` | Change the caller's own password — requires the *current* password even though already JWT-authenticated. | Customer |
| `GET /api/users/me/preferences` | Get preferences, creating a default row on first access. | Customer |
| `PUT /api/users/me/preferences` | Replace preferences. | Customer |
| `POST /api/passengers` | Save a passenger profile. | Customer |
| `GET /api/passengers/me` | List the caller's saved passenger profiles. | Customer |
| `PUT /api/passengers/{id}` | Update a saved passenger the caller owns. | Customer |
| `DELETE /api/passengers/{id}` | Delete a saved passenger the caller owns. | Customer |

## 6. Operator console — `RouteController`, `ScheduleManagementController`, `VehicleController`, `DriverController`, `FareRuleController`, `OperatorReportController`

All scoped to routes/vehicles/drivers/schedules the caller owns; a foreign-owned resource 404s rather than 403s.

| Method & path | Summary | Role |
|---|---|---|
| `POST /api/routes` | Create a route. | Operator |
| `PUT /api/routes/{id}` | Update an owned route. | Operator |
| `GET /api/routes/{id}/schedules` | List schedules under an owned route. | Operator |
| `GET /api/routes/{id}/fare-rules` | List fare rules under an owned route. | Operator |
| `GET /api/routes/mine` | List the caller's routes. | Operator |
| `POST /api/schedules` | Create a schedule under an owned route. | Operator |
| `PUT /api/schedules/{id}` | Update an owned schedule. | Operator |
| `POST /api/seats` | Add a seat to an owned schedule. | Operator |
| `PUT /api/seats/{id}` | Update an owned seat's status/fare. | Operator |
| `POST /api/vehicles` | Create a vehicle. | Operator |
| `GET /api/vehicles/mine` | List the caller's vehicles. | Operator |
| `POST /api/drivers` | Create a driver. | Operator |
| `GET /api/drivers/mine` | List the caller's drivers. | Operator |
| `POST /api/fare-rules` | Create one fare rule under an owned route. | Operator |
| `POST /api/fare-rules/bulk` | Bulk-load fare rules (e.g. parsed CSV). All-or-nothing — every row's ownership is validated before any row persists. | Operator |
| `GET /api/operator/reports` | Confirmed bookings, revenue, and occupancy per route the caller manages. | Operator |

## 7. Admin console — `UserController` (admin part), `PartnerController`, `PromoController`, `AuditController`, `ReconciliationController`

| Method & path | Summary | Role |
|---|---|---|
| `GET /api/users` | List every account. | Admin |
| `GET /api/users/{id}` | Get an account by id. | Admin |
| `POST /api/users` | Create an account with an explicit role — the provisioning path for operator/support/admin. | Admin |
| `PUT /api/users/{id}/role` | Reassign a role. An admin cannot change their own role, and the last remaining `ADMIN` cannot be demoted. | Admin |
| `POST /api/partners` | Onboard a partner (`PENDING`). | Admin |
| `GET /api/partners` | List every partner. | Admin |
| `GET /api/partners/{id}` | Get a partner. | Admin |
| `PUT /api/partners/{id}/status` | Move a partner between `PENDING`/`ACTIVE`/`SUSPENDED`. | Admin |
| `POST /api/partners/{id}/credentials` | Issue an OAuth2 client-credentials pair. **The secret is shown exactly once.** | Admin |
| `GET /api/partners/{id}/credentials` | List credentials (never a secret). | Admin |
| `PUT /api/partners/credentials/{credentialId}/revoke` | Revoke a credential; already-issued tokens still expire on their own short TTL. | Admin |
| `POST /api/partners/{id}/webhooks` | Register a webhook target. **The signing secret is shown exactly once.** | Admin |
| `GET /api/partners/{id}/webhooks` | List webhooks (never a secret). | Admin |
| `PUT /api/partners/webhooks/{webhookId}/status` | Enable/disable a webhook. | Admin |
| `POST /api/promos/validate` | Preview a promo code's discount without redeeming it. `subtotal` is caller-supplied and display-only — booking creation always recalculates the authoritative amount. | Public, rate-limited |
| `POST /api/promos` | Create a promo code. | Admin |
| `GET /api/promos` | List every promo code. | Admin |
| `PUT /api/promos/{id}/status` | Activate/deactivate a promo code. | Admin |
| `GET /api/audit?actor=&action=&entityType=&from=&to=&page=&size=` | Search the audit log, most recent first. `actor` is a substring match; `action`/`entityType` are exact. | Admin |
| `GET /api/ledger/reconciliation?from=&to=` | Aggregate payments/refunds/adjustments over a date range (`from` inclusive, `to` exclusive), backed by an append-only ledger. | Admin |

## 8. Partner API — `PartnerTokenController`, `PartnerResourceController`

| Method & path | Summary | Role |
|---|---|---|
| `POST /api/oauth/token` | OAuth2 client-credentials grant (simplified to JSON): exchange `client_id`/`client_secret` for a short-lived `PARTNER_API` bearer token. | Public, rate-limited per `client_id` |
| `GET /api/partner/routes` | List every route owned by the calling credential's partner. | Partner API |

---

### Coverage note

This table accounts for all 20 `@RestController` classes in `com.ticketwave.*.controller` and
all 69 of their mapped endpoints — nothing was omitted or summarized away. Request/response DTO
field-level detail is intentionally left to `/v3/api-docs` (the generated OpenAPI JSON) rather
than duplicated here, since that copy is guaranteed to stay in sync with the code and this one
is not.
