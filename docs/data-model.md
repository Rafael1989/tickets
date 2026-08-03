# TicketWave — Data Model

The persistence model: entities and their relationships, the enums that back
status columns, and the concurrency controls that keep seat inventory and money
correct under contention.

Schema is owned by Liquibase (`backend/src/main/resources/db/changelog/`) and
validated against the entities at startup (`ddl-auto: validate`), so this
document describes one thing, not two that might disagree.

---

## 1. Entity relationships

```
                    ┌─────────┐
                    │ Partner │
                    └────┬────┘
             ┌───────────┼────────────────┬──────────────────┐
             │           │                │                  │
       ┌─────▼─────┐  ┌──▼──────────────┐ │       ┌──────────▼─────────┐
       │   User    │  │ PartnerApi      │ │       │  PartnerWebhook    │
       │ (partner  │  │   Credential    │ │       └──────────┬─────────┘
       │  nullable)│  └─────────────────┘ │                  │
       └─┬───┬───┬─┘                      │       ┌──────────▼─────────┐
         │   │   │                        │       │ WebhookDeliveryLog │
         │   │   └──────────────┐         │       └────────────────────┘
         │   │                  │         │
   ┌─────▼──────┐        ┌──────▼──────┐  │
   │ Passenger  │        │ UserPrefer- │  │
   └─────┬──────┘        │   ences     │  │
         │               └─────────────┘  │
         │                                │
         │        ┌───────┐  operator     │
         │        │ Route │◀──────────────┘
         │        └───┬───┘
         │            │
         │     ┌──────▼─────┐      ┌──────────┐   ┌────────┐
         │     │  Schedule  │─────▶│ Vehicle  │   │ Driver │
         │     └──┬──────┬──┘      └──────────┘   └────▲───┘
         │        │      │                             │
         │        │      └─────────────────────────────┘
         │        │
         │   ┌────▼───┐
         │   │  Seat  │──── heldBy ────▶ User
         │   └────┬───┘
         │        │
    ┌────▼────────▼──────┐        ┌───────────┐
    │    BookingItem     │───────▶│  Booking  │──── promoCode ──▶ PromoCode
    └────────────────────┘        └─────┬─────┘
                                        │
                                  ┌─────▼─────┐
                                  │  Payment  │
                                  └─────┬─────┘
                                        │
                                  ┌─────▼─────┐      ┌──────────────┐
                                  │  Refund   │      │ LedgerEntry  │
                                  └───────────┘      └──────────────┘
                                                     (booking, payment, refund)

              ┌──────────┐         ┌──────────┐
              │ FareRule │         │ AuditLog │   (append-only, no FK)
              │ (route)  │         └──────────┘
              └──────────┘
```

---

## 2. Core entities

### `User` → `users`

| Field | Notes |
|---|---|
| `id` | PK |
| `username`, `email` | unique |
| `password` | BCrypt hash |
| `role` | `UserRole` |
| `partner` | **nullable** — null means a standalone operator, see `TenantScope` |

### `Passenger` → `passengers`
Saved traveller profiles owned by a `User`. Reused across bookings; every lookup
filters on ownership.

### `UserPreferences` → `user_preferences`
One row per user, created lazily on first access rather than at registration.

### `Route` → `routes`
Owned by an operator `User`. `type` is a `RouteType`. Carries
`origin`/`destination` for travel routes and `venue` for events — which of those
is populated depends on the type — plus `durationMinutes`.

### `Schedule` → `schedules`
A dated instance of a route: `departureTime`, `arrivalTime`, `baseFare`,
`currency`, `status`, plus optional `vehicle` and `driver`.

### `Seat` → `seats`

| Field | Notes |
|---|---|
| `schedule` | owning schedule |
| `seatNumber`, `seatClass` | `class` column |
| `status` | `SeatStatus` |
| `priceModifier` | `BigDecimal(6,3)`, multiplies the base fare |
| `heldUntil` | set while `HELD`; the instant after which the hold may be reclaimed |
| `heldBy` | who holds it — lets the same user re-affirm their own hold idempotently while still blocking everyone else |

### `Booking` → `bookings`

| Field | Notes |
|---|---|
| `user`, `schedule` | owner and trip |
| `pnr` | **unique**, 10 chars — the DB constraint is the source of truth for uniqueness |
| `status` | `BookingStatus` |
| `totalAmount` | `BigDecimal(12,2)` |
| `promoCode` | nullable |
| `idempotencyKey` | **unique**, nullable — a retried `POST /api/bookings` with the same key is rejected as a duplicate rather than double-holding seats. Null for callers that send none, which the unique constraint permits any number of |
| `version` | `@Version` optimistic lock |

### `BookingItem` → `booking_items`
One row per seat booked: `booking`, `seat`, `passenger`, `fare`. The fare is
frozen at booking time, not recomputed on read.

### `Payment` → `payments`
`booking`, `amount`, `method`, `reference` (**unique** — the idempotency
mechanism), `status`, `failureReason`, `paidAt`. Card numbers are **never
persisted**; they are read in memory only, to make the approve/decline decision.

### `Refund` → `refunds`
`payment`, `amount`, `policyCode`, `status`, `processedBy`, `processedAt`, plus
`overrideDelta` / `overrideReason` when an agent waives part of the fee.

### `LedgerEntry` → `ledger_entries`
Append-only financial record: `booking`, `payment`, optional `refund`,
`entryType`, `amount` (**refunds are stored negative**), `currency`,
`description`.

### `AuditLog` → `audit_logs`
Append-only: `actorUsername`, `action`, `entityType`, `entityId`, `details`,
`createdAt`. No foreign keys on purpose — an audit row must survive the deletion
of whatever it describes. Indexed for the search filters
(`2026-08-02-02-add-audit-log-search-indexes.xml`).

### Pricing
- `PromoCode` → `promo_codes` — `code`, `discountType`, `discountValue`,
  `validFrom`/`validTo`, `maxRedemptions`, `redemptionCount`, `active`.
- `FareRule` → `fare_rules` — operator-loaded surcharge per route + seat class
  over a date window.

### Partner
- `Partner` — `name`, `contactEmail`, `status`, `commissionRate`.
- `PartnerApiCredential` — OAuth2 `clientId` + `clientSecretHash` (**BCrypt**,
  same as a user password), `status`, `lastUsedAt`, `revokedAt`. The raw secret
  is returned exactly once, at issue time, and can never be retrieved again.
- `PartnerWebhook` — target `url`, `eventType`, `status`, and a `secret` stored
  **in plaintext**. It has to be: the secret is the HMAC-SHA256 key used to sign
  outbound payloads (`PartnerWebhookDeliveryService.sign`), so a one-way hash
  would make signing impossible. It is never returned by the list endpoint —
  only once, at registration — but unlike the credential secret it is recoverable
  from the database, so treat `partner_webhooks.secret` as sensitive at rest.
- `WebhookDeliveryLog` — per-attempt delivery outcome.

---

## 3. Enums

Every one is persisted through an explicit `AttributeConverter` writing a
lowercase string, so the database column is readable and stable rather than
ordinal-dependent.

| Enum | Values |
|---|---|
| `UserRole` | `CUSTOMER`, `OPERATOR`, `SUPPORT`, `ADMIN` |
| `RouteType` | `FLIGHT`, `BUS`, `TRAIN`, `EVENT` |
| `ScheduleStatus` | `SCHEDULED`, `DELAYED`, `CANCELLED`, `COMPLETED` |
| `SeatStatus` | `AVAILABLE`, `HELD`, `BOOKED`, `BLOCKED`, `RESERVED_OPERATOR` |
| `BookingStatus` | `INITIATED`, `PAYMENT_PROCESSING`, `CONFIRMED`, `FAILED`, `CANCELLED` |
| `PaymentStatus` | `PENDING`, `PENDING_3DS`, `SUCCEEDED`, `FAILED`, `REFUNDED` |
| `RefundStatus` | `PENDING`, `PROCESSED`, `REJECTED` |
| `DiscountType` | `PERCENTAGE`, `FIXED_AMOUNT` |
| `LedgerEntryType` | `PAYMENT`, `REFUND`, `ADJUSTMENT` |
| `PartnerStatus` | `PENDING`, `ACTIVE`, `SUSPENDED` |
| `PartnerCredentialStatus` | `ACTIVE`, `REVOKED` |
| `WebhookStatus` | `ACTIVE`, `DISABLED` |

`BLOCKED` and `RESERVED_OPERATOR` are operator-owned seat states — an operator
taking a seat out of sale, as distinct from a customer hold.

---

## 4. Concurrency controls

Four distinct mechanisms, each guarding a different failure:

### Pessimistic row locks — seat inventory

`SeatRepository.findByIdForUpdate` (`SELECT … FOR UPDATE`) backs every
hold/release/confirm and every operator seat edit. This is what stops an
operator's status change and a customer's concurrent checkout from silently
overwriting each other.

**Seats are always locked in ascending id order** (`BookingServiceImpl` sorts
selections before iterating), so two bookings racing over overlapping seat sets
can never deadlock on each other's locks.

### Optimistic locking — booking state

`Booking.version`. Two requests that both read `CONFIRMED` before either commits
would otherwise both produce a refund. The loser now fails with
`ObjectOptimisticLockingFailureException` → `409 CONCURRENT_UPDATE`.

`PaymentServiceImpl.markPaymentProcessingWithRetry` retries this up to 3 times,
going through `BookingService`'s transactional proxy each attempt so it genuinely
re-reads. (A `@Transactional` method cannot usefully catch and retry its own
failed commit from inside — the transaction is already gone by then.)

### Unique constraints — idempotency and identity

| Column | Guards |
|---|---|
| `bookings.pnr` | PNR collisions. `PnrGeneratorImpl` treats the constraint as the source of truth rather than a pre-check |
| `bookings.idempotency_key` | Duplicate booking creation → `409 DUPLICATE_BOOKING_REQUEST` |
| `payments.reference` | Double-charging. A replayed reference returns the original payment |
| `users.username`, `users.email` | Account identity |

Both idempotency paths catch `DataIntegrityViolationException`, but they resolve
it differently, and the difference is deliberate:

- **Payments** re-read and return the winner's row. Possible because
  `PaymentServiceImpl` has no single spanning transaction, so only the failed
  insert rolled back.
- **Bookings** throw `409` instead. Creation *is* one `@Transactional` method,
  and PostgreSQL aborts the whole transaction on a constraint violation — a
  same-transaction read-back would itself fail. The client's retry already holds
  the key and can just look the booking up.

### Row-level `UPDATE … WHERE` — the expired-hold sweep

`SeatRepository.releaseExpiredHolds(now)` is a single bulk statement, so the
background sweeper never holds a wide lock or races the on-access reclaim path.

---

## 5. Migrations

33 changelog files, chronological, one logical change each:

| Date | Covers |
|---|---|
| `2026-07-31-01` … `-12` | Core schema: users, passengers, routes, schedules, seats, bookings, booking items, payments, refunds, seat-hold expiration, promo codes |
| `2026-08-01-01` … `-11` | Audit log, user preferences, `seat.held_by`, payment states, failure reason, booking `@Version`, operator seat statuses, vehicles, drivers, schedule↔vehicle/driver, fare rules |
| `2026-08-02-01` … `-10` | Refund override fields, audit search indexes, partners, `user.partner_id`, partner credentials, partner webhooks, bookings status index, 3DS payment status, booking idempotency key, ledger entries |

Rules that are not optional here:

- One logical change per changeset.
- **Never edit a changeset that has shipped** — add a new one. Liquibase
  checksums are what make a shipped changeset immutable.
- Migrations always run against the primary datasource, never a replica
  (`spring.liquibase.url` is pinned to `${spring.datasource.url}`).

Because the schema is Liquibase-managed and uses PostgreSQL-specific behaviour,
repository and flow integration tests run against a **real (local) PostgreSQL**,
never H2 — see [`testing.md`](testing.md).
