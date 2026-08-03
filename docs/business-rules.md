# TicketWave — Business Rules

The domain logic, stated precisely enough to check an implementation against.
Every rule below is transcribed from the code, with the owning class named so it
can be verified rather than trusted.

The three areas with direct financial impact — **dynamic pricing**, **seat
holds**, and **refund proration** — are held to 100% line *and* branch coverage
by the build (see [`testing.md`](testing.md)).

---

## 1. Dynamic pricing

**Owner:** `PricingServiceImpl.calculateSeatFare`

```
fare = baseFare × seat.priceModifier × demandMultiplier      (2 dp, HALF_UP)
```

`demandMultiplier = max(1 + Σ adjustments, 0.10)`, where the adjustments stack
**additively**:

| Adjustment | Condition | Default |
|---|---|---|
| Last-minute surcharge | departure is in the future **and** ≤ 24h away | `+0.25` |
| Early-bird discount | departure ≥ 30 days away | `−0.10` |
| High-occupancy surcharge | occupancy ≥ 80% | `+0.15` |
| Low-occupancy discount | occupancy ≤ 20% | `−0.05` |
| Fare rules | sum of every active `FareRule` for this route + seat class at departure time | operator-defined |

Rules that are easy to get wrong:

- The time-based pair is **mutually exclusive** (`if / else if`) — a fare is
  never both last-minute and early-bird.
- The occupancy pair is likewise mutually exclusive.
- The two pairs are **independent** — a last-minute *and* high-occupancy seat
  takes both (`+0.40`).
- `occupancy = (totalSeats − availableSeats) / totalSeats`, so `HELD`, `BOOKED`,
  `BLOCKED` and `RESERVED_OPERATOR` all count as taken. Zero seats ⇒ occupancy 0.
- `MIN_DEMAND_MULTIPLIER = 0.10` is a defensive floor: no combination of
  adjustments, even under a misconfigured `PricingProperties`, can drive a fare
  to zero or negative.
- Fare rules stack additively with everything else — they are one more term, not
  an override.

### Promo codes

**Owner:** `PricingServiceImpl.applyPromoCode` / `previewPromoCode`

A code is usable only if **all** hold:

1. `active` is true,
2. now is within `[validFrom, validTo]`,
3. `maxRedemptions` is null, or `redemptionCount < maxRedemptions`.

Otherwise `PromoCodeNotApplicableException` names which one failed
(`inactive` / `outside its validity window` / `fully redeemed`).

```
PERCENTAGE   → discount = subtotal × value / 100   (2 dp, HALF_UP)
FIXED_AMOUNT → discount = value
```

The discount is capped at the subtotal — `discount.min(subtotal)` — so an order
can reach zero but never go negative.

`apply` vs `preview` is a real distinction, not a naming quirk:

| | Locking | Redemption count |
|---|---|---|
| `applyPromoCode` | `findByCodeForUpdate` (row lock) | **incremented** |
| `previewPromoCode` | plain read, `readOnly` | untouched |

`POST /api/promos/validate` is display-only: its `subtotal` is caller-supplied,
and booking creation always recalculates the authoritative amount server-side.

---

## 2. Seat holds

**Owner:** `SeatHoldServiceImpl` (+ `SeatHoldExpirationScheduler`)

Every operation starts from `findByIdForUpdate` — a pessimistic row lock.

### `holdSeat`

Succeeds when the seat is `AVAILABLE`, **or** its hold has expired, **or** the
caller already holds it. Otherwise `SeatUnavailableException`.

That third clause is what makes holding idempotent: a repeat call by the same
holder — including the implicit one at booking creation — *refreshes* the hold
rather than being rejected. On success, `heldUntil = now + 10 minutes`
(configurable) and `heldBy = caller`.

### `releaseSeat`

Releases `HELD` **and `BOOKED`** seats. The `BOOKED` case is not an oversight:
cancelling an already-paid booking must return the seat to inventory, and the
refund flow drives that path through `BookingService.cancelBooking`.

### `releaseOwnHold`

Only acts if the caller is the current holder. For anyone else it is a **silent
no-op** — deliberately, so the endpoint never reveals a seat's actual state to a
non-holder. Idempotent by construction.

### `confirmHold`

`HELD` and unexpired ⇒ `BOOKED`, clearing `heldUntil`/`heldBy`. An expired hold
throws even though the row still reads `HELD`.

### Expiration — two independent paths

1. **On access.** Any hold attempt on a seat whose `heldUntil` has passed treats
   it as available and reclaims it.
2. **Background sweep.** `SeatHoldExpirationScheduler` runs every 60s
   (configurable) and bulk-releases expired holds.

The sweeper exists because on-access reclaim only fires when someone actually
asks for that specific seat. Without it, an abandoned hold on an unpopular seat
would sit `HELD` forever and undercount availability.

---

## 3. Booking lifecycle

**Owner:** `BookingServiceImpl`

```
                    ┌─────────────┐
      create ──────▶│  INITIATED  │
                    └──────┬──────┘
                           │ markPaymentProcessing
                           ▼
                 ┌──────────────────────┐
        ┌───────▶│  PAYMENT_PROCESSING  │──── confirm ────▶┌───────────┐
        │        └──────────┬───────────┘                  │ CONFIRMED │
        │                   │ fail                         └─────┬─────┘
        │                   ▼                                    │
        │            ┌──────────┐                                │
        └── retry ───│  FAILED  │                                │
                     └────┬─────┘                                │
                          │                                      │
                          │           ┌─────────────┐            │
                          └──────────▶│  CANCELLED  │◀───────────┘
                                      └─────────────┘
                                       ▲
                        INITIATED ─────┘
```

| Transition | Allowed from | Notes |
|---|---|---|
| `markPaymentProcessing` | `INITIATED`, `FAILED`, `PAYMENT_PROCESSING` | Re-affirming from `PAYMENT_PROCESSING` is an idempotent no-op, not an error. Rejecting `CONFIRMED`/`CANCELLED` is what stops a stray payment reopening a settled booking |
| `confirmBooking` | `PAYMENT_PROCESSING` | Confirms every seat hold → `BOOKED` |
| `failBooking` | `PAYMENT_PROCESSING` | **Does not release seats** — a decline should let the customer retry the same seats, not lose them |
| `cancelBooking` | anything except `CANCELLED` and `PAYMENT_PROCESSING` | Releases all seats. A payment in flight must not be cancelled out from under itself |

**Creation** (`createBooking`) in order: idempotency-key short-circuit → resolve
user and schedule → insert the booking → sort seat selections by id → per seat,
verify passenger ownership, hold the seat, price it, persist a `BookingItem` →
apply any promo code → set the total.

**PNR** is generated by `PnrGeneratorImpl`; the unique constraint on
`bookings.pnr` is the real source of truth, not a pre-check.

**Guest lookup** (`lookupByPnrAndEmail`) requires PNR **and** the account email
as a second factor, so a bare PNR guess cannot retrieve someone else's itinerary.

**Staff search** (`searchBookings`) matches an exact PNR or a substring of
customer email / passenger name, max 25 results, newest first. A blank query
returns an empty list rather than everything. LIKE wildcards in user input are
escaped.

---

## 4. Payments

**Owner:** `PaymentServiceImpl`

### Idempotency

Keyed on `request.reference()`:

1. Pre-check `findByReference` — a replay returns the original response.
2. Amount must equal the booking total, checked *before*
   `markPaymentProcessing` so a mismatch never strands the booking in
   `PAYMENT_PROCESSING`.
3. On insert, a `DataIntegrityViolationException` from the unique constraint
   means a concurrent request with the same reference won — read back and return
   its result.
4. If `markPaymentProcessing` throws `InvalidBookingStateException` but a payment
   with this reference now exists, the racing request completed the whole flow;
   return its payment rather than surfacing an error for what is, from the
   caller's view, an idempotent retry.

`PaymentServiceImpl` has **no method-level `@Transactional` spanning the flow**,
on purpose: the insert and the booking transition are independently transactional
so a constraint violation rolls back only that statement's transaction, leaving
the recovery read able to run. The tradeoff — a small window where the process
could die between steps — is accepted and documented in the class Javadoc.

### Card decisions

`CardDeclineSimulator` stands in for a payment gateway, using Stripe's own
well-known test PANs so the checkout UI can point demo users at recognisable
cards:

| Card | Outcome |
|---|---|
| `4000000000000002` | `Your card was declined.` |
| `4000000000009995` | `Insufficient funds.` |
| `4000000000000069` | `Your card has expired.` |
| `4000002500003155` | Requires a 3DS challenge |
| anything else / none | Approved |

Card numbers are space-normalised before matching and **never persisted**.

### 3D Secure

A 3DS-required card produces a `PENDING_3DS` payment and leaves the booking in
`PAYMENT_PROCESSING` — neither confirmed nor failed until the challenge resolves.
`POST /api/bookings/{id}/payments/{paymentId}/confirm-3ds` settles it: code
`123456` succeeds, anything else fails it exactly like a decline.

`confirmThreeDs` explicitly re-`save`s the payment. With `open-in-view` disabled
the entity is detached by that point, so without the save the response would
report `SUCCEEDED` while the row stayed `PENDING_3DS` forever.

### Ledger

A successful payment appends a `LedgerEntry`. It is recorded through
`recordLedgerPaymentSafely`, which catches and **logs at ERROR** rather than
propagating: the charge has already succeeded and the booking is already
confirmed, so letting a ledger blip turn into a 500 would hide a real payment
from the customer. A missing entry is a reconciliation gap for someone to
backfill, not a failed payment.

---

## 5. Cancellation and refunds

**Owners:** `RefundPolicyService` (the window), `RefundServiceImpl` (the flow)

### The policy window

| Time until departure | Outcome | Policy code |
|---|---|---|
| ≥ 7 days | 100% refund | `FULL_REFUND` |
| ≥ 24h and < 7 days | 50% refund | `PARTIAL_REFUND` |
| < 24h, or already departed | **Cancellation blocked entirely** | — |

Blocked means blocked: the booking cannot be cancelled at all, not merely
cancelled without a refund. The same window also gates whether a paid booking may
be **rescheduled** — too close to cancel is too close to reschedule.

`refundAmount = payment.amount × rate` (2 dp, HALF_UP);
`nonRefundable = payment.amount − refundAmount`.

### Two-step settlement

**Request** (`initiateRefund`, customer or staff) creates a `PENDING` refund —
and the booking deliberately **stays `CONFIRMED` and keeps its seats**.
Cancelling up front would free the seats for resale, so a later rejection could
leave the customer with neither the trip nor the money. Only one pending request
per booking (`409 REFUND_ALREADY_PENDING`).

**Settlement** (`processRefund`, support/admin only):

| Decision | Effect |
|---|---|
| **Approve** a cancellation refund | Payment → `REFUNDED`, booking cancelled, seats released, `BOOKING_CANCELLED` webhook fired, ledger entry appended |
| **Approve** a `RESCHEDULE_CREDIT` | Ledger entry appended. Payment **stays `SUCCEEDED`** and the booking stays `CONFIRMED` — it is still travelling, just at a reduced net amount |
| **Reject** | Genuine no-op on the booking: it stays `CONFIRMED`, the payment is untouched |

### Fee override

On approval only, an agent may waive part or all of the fee. A **reason is
mandatory** (`RefundOverrideReasonRequiredException`) and the amount may not
exceed the original payment. The signed delta and reason are stored on the refund
and written as a distinct `REFUND_FEE_OVERRIDDEN` audit entry, separate from the
ordinary `REFUND_APPROVED` one.

---

## 6. Reschedule

**Owner:** `RescheduleServiceImpl` (orchestration) over
`BookingServiceImpl.rescheduleBooking` (the mechanical swap)

| Booking status | Rules |
|---|---|
| `INITIATED` | Free, regardless of fare delta. No proximity window applies to an unpaid booking |
| `CONFIRMED` | Must pass the `RefundPolicyService` window, then the fare difference is settled |
| anything else | `InvalidBookingStateException` |

For a confirmed booking:

```
difference = newTotal − oldTotal
  > 0  →  collect a top-up on the existing payment
  < 0  →  issue a RESCHEDULE_CREDIT refund (PENDING)
  = 0  →  nothing
```

- **Top-up** requires both `paymentMethod` and `paymentReference`
  (`FareDifferencePaymentRequiredException` otherwise) and runs through the same
  decline simulator (`FareDifferenceDeclinedException`).
- The top-up **adjusts the existing `Payment`'s amount** rather than inserting a
  second row, so it stays the single source of truth for the cancellation math
  that `initiateRefund` later relies on.
- The seat/schedule swap happens **first**, inside the same transaction. If the
  fare difference is then missing or declined, throwing rolls back seats,
  schedule and total together — nothing is left half-applied.

`GET /api/bookings/{id}/reschedule-quote` previews all of this without
committing, reporting `eligible` and `paymentRequired` separately.

---

## 7. Operator tenancy

**Owner:** `TenantScope.isSameTenant(resourceOwner, caller)`

True when the caller **is** the resource's owner, or when both users belong to
the **same non-null `Partner`**. An operator with `partner == null` is a
standalone silo and only ever matches itself.

Applied to routes, schedules, seats, vehicles, drivers and fare rules. The check
sits inside the `Optional` chain, so a foreign-owned resource produces a
**404, not a 403** — the API never confirms someone else's resource exists.

`POST /api/fare-rules/bulk` is all-or-nothing: every row's ownership is validated
before any row is persisted.

---

## 8. Reporting and reconciliation

**Operator report** (`OperatorReportServiceImpl`) — per route the caller manages:
confirmed bookings, revenue, total/booked seats, and
`occupancyRate = bookedSeats / totalSeats` (4 dp, HALF_UP; zero seats ⇒ zero).
Routes with no booking or seat rows yet report zeros rather than being omitted.
An operator belonging to a partner sees the **partner's** routes, not just their
own.

**Reconciliation** (`LedgerServiceImpl.reconcile`, admin only) aggregates the
append-only ledger over `[from, to)` — `from` inclusive, `to` exclusive.
Refunds are stored negative, so:

```
net = totalPayments + totalRefunds + totalAdjustments
```

while the reported refund and adjustment **totals are shown as absolute values**.

---

## 9. Audit

`AuditService.record(actor, action, entityType, entityId, details)` writes an
append-only row. Actions currently recorded include `REFUND_INITIATED`,
`REFUND_APPROVED`, `REFUND_REJECTED`, `REFUND_FEE_OVERRIDDEN`,
`RESCHEDULE_FARE_COLLECTED`, `RESCHEDULE_CREDIT_ISSUED`, `SEAT_ADDED`,
`SEAT_UPDATED`.

Search (`GET /api/audit`, admin) filters by actor (case-insensitive substring),
action and entity type (exact, case-insensitive), and a created-at range — all
optional; an all-null criteria matches everything. LIKE wildcards in the actor
filter are escaped.
