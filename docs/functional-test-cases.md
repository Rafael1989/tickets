# TicketWave — Functional Test Cases (Booking & Payment Flows)

Companion to [`functional-specification.md`](functional-specification.md). Every row below
names a real, currently-passing test — not a proposed one — identified by its actual method
name and file:line, following the repo's `methodName_condition_expectedResult` convention
([`CLAUDE.md`](../CLAUDE.md)). Nothing here was invented: this is the test suite read back as
a specification, grouped by flow instead of by class.

**Scope:** the booking and payment lifecycle end to end — booking creation & idempotency, seat
hold & expiration, PNR generation, payment recording & 3-D Secure, reschedule & fare
settlement, and refunds. (Search, dynamic pricing, and admin/operator management have their own
test classes outside this scope — see `functional-specification.md` for the full endpoint list.)

## Coverage summary

| Flow | Unit | Integration (local PostgreSQL) | Controller (MockMvc) |
|---|---|---|---|
| Booking creation & lifecycle | `BookingServiceImplTest` (44 cases) | `BookingFlowIT` (4 cases) | `BookingControllerTest` (19 cases) |
| PNR generation | `PnrGeneratorImplTest` (3 cases) | — | — |
| Seat hold & expiration | `SeatHoldServiceImplTest` (21 cases) | `SeatHoldConcurrencyIT` (1 case) | — |
| Payment & idempotency | `PaymentServiceImplTest` (21 cases) | `PaymentFlowIT` (5 cases) | via `BookingControllerTest` |
| Reschedule & fare settlement | `RescheduleServiceImplTest` (17 cases) | — | via `BookingControllerTest` |
| Refunds | `RefundServiceImplTest` (26 cases) | `RefundFlowIT` (8 cases) | `RefundControllerTest` (4 cases), via `BookingControllerTest` |
| Ownership / RBAC | `BookingOwnershipTest` (2 cases) | — | enforced across all controller cases above |

All classes above are part of the current **670-test, 0-failure** run (`mvn clean test`);
`PricingServiceImpl`, `SeatHoldServiceImpl`, `SeatHoldExpirationScheduler`, and
`RefundPolicyService` are held to the repo's 100% line+branch gate specifically because they
carry financial risk. The bundle-wide gate is now 80% **line and branch** — see
[`testing.md`](testing.md) for the measured figures and what the JaCoCo exclusions cover.

Also in the payment package, outside the flow tables below: `CardDeclineSimulatorTest`
(9 cases) pins the simulated gateway's approve/decline/3DS card matching, including
space-grouped card numbers and the no-card-number path.

---

## 1. Booking creation & idempotency

| Case | Scenario | Expected outcome | Automated by |
|---|---|---|---|
| BK-01 | Customer creates a booking for one or more seats | Seats are held in ascending id order; booking total is the sum of their fares | `createBooking_holdsSeatsInAscendingIdOrderAndSumsFares` — `BookingServiceImplTest.java:113` |
| BK-02 | The same idempotency key is submitted twice | The existing booking is returned; no new seat holds are created | `createBooking_withIdempotencyKeyOfExistingBooking_returnsExistingBookingWithoutCreatingSeatHolds` — `:163` |
| BK-03 | Two requests race on the same idempotency key at the DB level | The insert loser gets `DuplicateBookingRequestException`, not a raw DB error | `createBooking_withIdempotencyKeyLosingRaceOnInsert_throwsDuplicateBookingRequestException` — `:188` |
| BK-04 | A valid promo code is supplied | Discount is applied and the promo code is recorded on the booking | `createBooking_withPromoCode_appliesDiscountAndRecordsPromoCodeOnBooking` — `:206` |
| BK-05 | A blank promo code string is supplied | Treated identically to no promo code — no error, no discount | `createBooking_withBlankPromoCode_skipsPromoApplicationJustLikeNull` — `:247` |
| BK-06 | Referenced user, schedule, or passenger doesn't exist | Fails fast with the matching `*NotFoundException` before any seat is held | `createBooking_whenUserMissing_…` `:285`, `createBooking_whenScheduleMissing_…` `:297`, `createBooking_whenPassengerMissing_…` `:308` |
| BK-07 | Passenger belongs to a different user | `PassengerNotFoundException` (never leaks another user's passenger) | `createBooking_whenPassengerBelongsToAnotherUser_throwsPassengerNotFoundException` — `:326` |
| BK-08 | A requested seat is already held/booked | `SeatUnavailableException` propagates from the seat-hold layer | `createBooking_whenSeatUnavailable_propagatesSeatUnavailableException` — `:347` |
| BK-09 | Two customers submit overlapping seat requests in opposite lock order, concurrently | Neither deadlocks; exactly one succeeds per seat, no double-booking | `concurrentBookingsOverTheSameSeatsInOppositeOrder_neitherDeadlocksNorDoubleBooks` — `BookingFlowIT.java:173` |
| BK-10 | Full create → confirm round trip against a real DB | Seats move to `BOOKED`, booking moves to `CONFIRMED` | `createThenConfirm_movesSeatsToBookedAndBookingToConfirmed` — `BookingFlowIT.java:133` |
| BK-11 | Full create → cancel round trip against a real DB | Seats are released back to `AVAILABLE` | `createThenCancel_releasesSeatsBackToAvailable` — `BookingFlowIT.java:156` |
| BK-12 | `POST /api/bookings` without a bearer token | 401, booking never created | `createBooking_withoutAuthorizationHeader_isRejected` — `BookingControllerTest.java:98` |
| BK-13 | `POST /api/bookings` with a valid token and payload | 201 Created | `createBooking_withValidTokenAndPayload_returns201` — `:108` |

## 2. Booking lifecycle & state transitions

| Case | Scenario | Expected outcome | Automated by |
|---|---|---|---|
| BK-14 | Confirm a booking currently `PAYMENT_PROCESSING` | Every seat item is confirmed; booking becomes `CONFIRMED` | `confirmBooking_whenPaymentProcessing_confirmsEachSeatAndTransitionsToConfirmed` — `BookingServiceImplTest.java:366` |
| BK-15 | Confirm a booking not in `PAYMENT_PROCESSING` | `InvalidBookingStateException`; no seat is touched | `confirmBooking_whenNotPaymentProcessing_throwsInvalidBookingStateExceptionWithoutTouchingSeats` — `:387` |
| BK-16 | `PUT /api/bookings/{id}/confirm` when already `CONFIRMED` | 200 (compatibility no-op) | `confirmBooking_whenAlreadyConfirmed_returns200` — `BookingControllerTest.java:121` |
| BK-17 | `PUT /api/bookings/{id}/confirm` when not yet confirmed | 409 (never changes state itself) | `confirmBooking_whenNotYetConfirmed_returns409` — `:134` |
| BK-18 | Mark `INITIATED` or `FAILED` booking as payment-processing | Transitions to `PAYMENT_PROCESSING` (failed bookings can retry) | `markPaymentProcessing_whenInitiated_…` `:408`, `markPaymentProcessing_whenFailed_…AsARetry` `:422` |
| BK-19 | Mark an already-`CONFIRMED` booking as payment-processing | `InvalidBookingStateException` | `markPaymentProcessing_whenAlreadyConfirmed_throwsInvalidBookingStateException` — `:436` |
| BK-20 | Mark an already-`PAYMENT_PROCESSING` booking again | No-op reaffirmation, not an error | `markPaymentProcessing_whenAlreadyPaymentProcessing_isANoOpReaffirm` — `:445` |
| BK-21 | Fail a booking mid payment-processing | Transitions to `FAILED`; seats stay held (so a retry can still confirm) | `failBooking_whenPaymentProcessing_transitionsToFailedWithoutReleasingSeats` — `:463` |
| BK-22 | Cancel an `INITIATED`, `CONFIRMED`, or `FAILED` booking | Every seat item is released; booking becomes `CANCELLED` | `cancelBooking_whenInitiated_…` `:491`, `cancelBooking_whenConfirmed_…` `:510`, `cancelBooking_whenFailed_…` `:554` |
| BK-23 | Cancel an already-`CANCELLED` or `PAYMENT_PROCESSING` booking | `InvalidBookingStateException`; nothing touched | `cancelBooking_whenAlreadyCancelled_…` `:532`, `cancelBooking_whenPaymentProcessing_…` `:543` |
| BK-24 | Lookups: by id, by PNR, "confirmed-only" guard, guest PNR+email | Correct booking + items returned, or the matching `*NotFoundException` | `getBooking_*`, `getBookingByPnr_*`, `requireConfirmed_*`, `lookupByPnrAndEmail_*` — `:573‑692` |
| BK-25 | Support/Admin free-text search (`GET /api/bookings/search?query=`) | Blank query short-circuits to empty list without hitting the repository; a real query returns mapped matches | `searchBookings_withBlankQuery_…` `:700`, `searchBookings_withMatches_…` `:708` |
| BK-26 | Guest "find my booking" (`GET /api/bookings/pnr/{pnr}/lookup?email=`) without a token | 200 — deliberately public, gated by PNR+email instead of auth | `lookupByPnrAndEmail_withoutAnyAuthorizationHeader_returns200` — `BookingControllerTest.java:281` |
| BK-27 | Booking ownership check used by `@PreAuthorize` SpEL | True only when the booking's customer matches the caller's username | `isOwnedBy_whenBookingBelongsToUsername_returnsTrue` / `…SomeoneElse_returnsFalse` — `BookingOwnershipTest.java:19,27` |
| BK-28 | Customer lists their own bookings (`GET /api/bookings/me`) | Only that customer's own bookings, newest first, every status included | `listMyBookings_returnsTheUsersOwnBookingsNewestFirst` — `BookingServiceImplTest.java:700` |
| BK-29 | Listing bookings for a username that doesn't resolve to a user | `UserNotFoundException` | `listMyBookings_whenUserMissing_throwsUserNotFoundException` — `BookingServiceImplTest.java:719` |
| BK-30 | `GET /api/bookings/me` with / without a bearer token | 200 with the caller's rows; 401 without a token | `listMyBookings_withValidToken_returns200` / `listMyBookings_withoutAuthorizationHeader_isRejected` — `BookingControllerTest.java:259,270` |

## 3. PNR generation

| Case | Scenario | Expected outcome | Automated by |
|---|---|---|---|
| PNR-01 | Generate a PNR | Six characters, drawn from the unambiguous alphabet (no `0/O/1/I` confusion) | `generate_returnsSixCharacterCodeFromTheUnambiguousAlphabet` — `PnrGeneratorImplTest.java:22` |
| PNR-02 | A generated candidate collides with an existing PNR | Retries with a new candidate rather than failing | `generate_retriesWhenACandidateCollides` — `:34` |
| PNR-03 | Every retry attempt collides | Fails loudly (`IllegalStateException`) instead of silently issuing a duplicate | `generate_whenEveryAttemptCollides_throwsIllegalStateException` — `:46` |

## 4. Seat hold & expiration

*`SeatHoldServiceImpl` and `SeatHoldExpirationScheduler` are held to the repo's 100% line+branch coverage gate.*

| Case | Scenario | Expected outcome | Automated by |
|---|---|---|---|
| SH-01 | Hold an `AVAILABLE` seat | Marked `HELD` with a TTL expiration and the caller as owner | `holdSeat_whenAvailable_marksHeldWithExpirationAndOwner` — `SeatHoldServiceImplTest.java:48` |
| SH-02 | Hold a seat already held by someone else, not yet expired | `SeatUnavailableException` | `holdSeat_whenHeldBySomeoneElseAndNotExpired_throwsSeatUnavailableException` — `:62` |
| SH-03 | Caller re-holds their own still-valid hold | TTL is refreshed/extended, not re-created | `holdSeat_whenHeldByCallerAndNotExpired_reaffirmsAndExtendsTtlInsteadOfThrowing` — `:71` |
| SH-04 | Hold a seat whose previous hold has expired | Reclaimed for the new holder | `holdSeat_whenHeldButExpired_reclaimsSeatForNewHolder` — `:85` |
| SH-05 | Hold a `BOOKED` seat | `SeatUnavailableException` | `holdSeat_whenBooked_throwsSeatUnavailableException` — `:110` |
| SH-06 | Release a hold: caller owns it / someone else owns it / not held at all | Released only when the caller is the owner; otherwise a silent no-op that never reveals the seat's real state | `releaseOwnHold_whenHeldByCaller_releasesIt` `:181`, `…SomeoneElse_isSilentNoOp` `:193`, `…NotHeldAtAll_isSilentNoOp` `:216` |
| SH-07 | Confirm a hold (booking → payment) while still valid | Seat becomes `BOOKED`, owner cleared | `confirmHold_whenHeldAndNotExpired_marksBookedAndClearsOwner` — `:226` |
| SH-08 | Confirm a hold that has since expired, or was never held | `SeatUnavailableException` — the caller loses the seat rather than silently booking it anyway | `confirmHold_whenHoldExpired_throwsSeatUnavailableException` `:251`, `confirmHold_whenNotHeld_…` `:260` |
| SH-09 | Background sweep releases expired holds | Delegates to the repository's bulk-update query | `releaseExpiredHolds_delegatesToRepositoryBulkUpdate` — `:269` |
| SH-10 | 20 concurrent requests hold the same seat simultaneously | Exactly one succeeds; the rest fail cleanly, none corrupt the row | `holdSeat_underConcurrentRequests_onlyOneAttemptSucceeds` — `SeatHoldConcurrencyIT.java:56` |

## 5. Payment recording, idempotency & 3-D Secure

*`PricingServiceImpl`-derived totals feed this flow; `PaymentServiceImpl` enforces the idempotency guarantee end to end.*

| Case | Scenario | Expected outcome | Automated by |
|---|---|---|---|
| PAY-01 | Submit a payment with a `reference` already used | Returns the existing payment; skips re-charging entirely | `recordPayment_whenReferenceAlreadyUsed_returnsExistingPaymentAndSkipsEverythingElse` — `PaymentServiceImplTest.java:67` |
| PAY-02 | Pay against a missing booking, or one not awaiting payment | Matching `*NotFoundException` / `InvalidBookingStateException` | `recordPayment_whenBookingMissing_…` `:84`, `recordPayment_whenBookingNotAwaitingPayment_…` `:94` |
| PAY-03 | Submitted amount doesn't match the booking total | `PaymentAmountMismatchException`; booking is never marked processing | `recordPayment_whenAmountDoesNotMatchBookingTotal_throwsPaymentAmountMismatchExceptionWithoutMarkingProcessing` — `:178` |
| PAY-04 | Happy path payment | Payment saved `SUCCEEDED`; booking confirmed | `recordPayment_happyPath_savesSucceededPaymentAndConfirmsBooking` — `:193`; end-to-end via `recordPayment_happyPath_confirmsBookingAndBooksTheSeat` — `PaymentFlowIT.java:127` |
| PAY-05 | Known decline-card is used | Payment saved `FAILED` with a reason; booking fails without confirming | `recordPayment_withKnownDeclineCard_savesFailedPaymentAndFailsBookingWithoutConfirming` — `PaymentServiceImplTest.java:266` |
| PAY-06 | Decline, then retry with a good card | First attempt fails; retry succeeds and confirms — without losing the seat hold in between | `recordPayment_withDeclineCardThenRetryWithGoodCard_failsThenConfirmsWithoutLosingTheSeat` — `PaymentFlowIT.java:193` |
| PAY-07 | The same `reference` is replayed after the original succeeded | Idempotent — returns the original result | `recordPayment_replayedWithSameReference_isIdempotent` — `PaymentFlowIT.java:146` |
| PAY-08 | N concurrent requests share the same `reference` | Exactly one `Payment` row and one booking confirmation are produced | `recordPayment_concurrentRequestsWithSameReference_produceExactlyOnePaymentAndOneConfirmation` — `PaymentFlowIT.java:162` |
| PAY-09 | Concurrent same-reference insert loses the DB race | Recovers by re-reading the winner's row rather than erroring | `recordPayment_whenConcurrentDuplicateReferenceInsert_recoversTheWinningPayment` — `PaymentServiceImplTest.java:294`; `…WhenConcurrentSameReferenceRequestAlreadyConfirmedTheBooking_recoversTheWinnersPayment` — `:120` |
| PAY-10 | Recovery read finds nothing after a failed duplicate insert | Rethrows the original exception rather than swallowing it | `recordPayment_whenDuplicateInsertFailsAndRecoveryReadFindsNothing_rethrowsOriginalException` — `:314` |
| PAY-11 | Optimistic-lock race while marking the booking payment-processing | Retries automatically once, then succeeds; if it keeps losing, rethrows after max attempts | `recordPayment_whenMarkPaymentProcessingLosesAnOptimisticLockRaceOnce_retriesAndSucceeds` `:142`; `…KeepsLosingTheOptimisticLockRace_rethrowsAfterMaxAttempts` `:161` |
| PAY-12 | Card requires 3-D Secure | Payment saved `PENDING_3DS`; booking left unsettled pending the challenge | `recordPayment_withThreeDsRequiredCard_savesPending3dsAndLeavesBookingUnsettled` — `:327` |
| PAY-13 | Confirm the 3DS challenge with the right / wrong code | Right code succeeds the payment and confirms the booking; wrong code fails it exactly like a decline | `confirmThreeDs_withValidCode_…` `:307`, `confirmThreeDs_withWrongCode_…` `:325` |
| PAY-14 | Confirm 3DS for a payment not in `PENDING_3DS`, or belonging to a different booking | `InvalidPaymentStateException` / `PaymentNotFoundException` | `confirmThreeDs_whenPaymentNotPending3ds_…` `:361`, `confirmThreeDs_whenPaymentBelongsToDifferentBooking_…` `:343` |
| PAY-15 | `POST /api/bookings/{id}/payments` with a valid token | 201 Created | `recordPayment_withValidTokenAndPayload_returns201` — `BookingControllerTest.java:144` |
| PAY-16 | `POST /api/bookings/{id}/payments/{paymentId}/confirm-3ds` with a valid token | 200 | `confirmThreeDs_withValidToken_returns200` — `:190` |

## 6. Reschedule & fare settlement

| Case | Scenario | Expected outcome | Automated by |
|---|---|---|---|
| RS-01 | Preview reschedule for an `INITIATED` booking | Always eligible, never requires a payment (nothing charged yet) | `previewReschedule_forInitiatedBooking_isAlwaysEligibleAndNeverRequiresPayment` — `RescheduleServiceImplTest.java:123` |
| RS-02 | Preview an upgrade on a `CONFIRMED` booking, far from departure | Requires a top-up payment for the fare difference | `previewReschedule_forConfirmedBooking_farFromDeparture_upgrade_requiresPayment` — `:139` |
| RS-03 | Preview a downgrade on a `CONFIRMED` booking | No payment required (credit path instead) | `previewReschedule_forConfirmedBooking_downgrade_doesNotRequirePayment` — `:155` |
| RS-04 | Preview too close to departure | Ineligible | `previewReschedule_forConfirmedBooking_tooCloseToDeparture_isIneligible` — `:171` |
| RS-05 | Actually reschedule an `INITIATED` booking | Delegates directly — no policy or billing checks apply pre-confirmation | `reschedule_forInitiatedBooking_delegatesDirectlyWithNoPolicyOrBillingChecks` — `:226` |
| RS-06 | Reschedule a `CONFIRMED` booking with no fare difference | Neither payment nor refund is touched | `reschedule_forConfirmedBooking_noFareDifference_touchesNeitherPaymentNorRefund` — `:240` |
| RS-07 | Upgrade with valid top-up payment details | Payment amount increased | `reschedule_forConfirmedBooking_upgradeWithValidPayment_increasesPaymentAmount` — `:256` |
| RS-08 | Upgrade without payment details, or with a declined card | Throws without persisting anything / leaves the original payment untouched | `…upgradeWithoutPaymentDetails_throwsAndNeverPersistsAnything` `:272`, `…upgradeDeclined_throwsAndLeavesPaymentUntouched` `:288` |
| RS-09 | Downgrade a `CONFIRMED` booking | Payment reduced; a `RESCHEDULE_CREDIT` refund is issued as `PENDING` | `reschedule_forConfirmedBooking_downgrade_reducesPaymentAndIssuesPendingCreditRefund` — `:303` |
| RS-10 | Reschedule too close to departure | `CancellationNotAllowedException` (same proximity window as cancellation) | `reschedule_forConfirmedBooking_tooCloseToDeparture_throwsCancellationNotAllowedException` — `:323` |
| RS-11 | Reschedule a `CANCELLED` booking | `InvalidBookingStateException` | `reschedule_whenBookingCancelled_throwsInvalidBookingStateException` — `:348` |
| RS-12 | `PUT /api/bookings/{id}/reschedule` / `GET .../reschedule-quote` with a valid token | 200 | `rescheduleBooking_withValidToken_returns200` / `previewReschedule_withValidToken_returns200` — `BookingControllerTest.java:225,259` |

## 7. Refunds

*`RefundPolicyService` is held to the repo's 100% line+branch coverage gate.*

| Case | Scenario | Expected outcome | Automated by |
|---|---|---|---|
| RF-01 | Request a cancellation far from departure | Full-refund quote created as `PENDING`; **booking stays `CONFIRMED` and keeps its seats** pending review | `initiateRefund_whenFarFromDeparture_appliesFullRefundAndLeavesTheBookingConfirmedForReview` — `RefundServiceImplTest.java:123`; e2e `initiateRefund_farFromDeparture_quotesAFullRefundButLeavesTheBookingConfirmed` — `RefundFlowIT.java:132` |
| RF-01b | Request a second cancellation while one is under review | `RefundAlreadyPendingException` (409); no second refund row | `initiateRefund_whenOneIsAlreadyAwaitingReview_throwsRefundAlreadyPendingException` — `RefundServiceImplTest.java:153`; e2e `initiateRefund_whenAlreadyAwaitingReview_isRejectedInsteadOfRaisingASecondRefund` — `RefundFlowIT.java:149` |
| RF-02 | Initiate within the partial-refund window | Prorated refund amount | `initiateRefund_withinPartialWindow_appliesProratedRefund` — `RefundServiceImplTest.java:168`; e2e `initiateRefund_withinPartialWindow_proratesTheRefund` — `RefundFlowIT.java:162` |
| RF-03 | Initiate too close to departure (or after departure) | `CancellationNotAllowedException`; booking/refund untouched | `initiateRefund_tooCloseToDeparture_throws…` `:173`, `initiateRefund_whenDepartureAlreadyInThePast_throws…` `:190`; e2e `initiateRefund_tooCloseToDeparture_isBlockedAndLeavesBookingConfirmed` — `RefundFlowIT.java:173` |
| RF-04 | Refund a missing booking, a not-yet-confirmed booking, or one with no successful payment | Matching `*NotFoundException` / `InvalidBookingStateException` | `initiateRefund_whenBookingMissing_…` `:211`, `…NotConfirmed_…` `:220`, `…NoSuccessfulPaymentExists_…` `:230` |
| RF-05 | Preview a refund quote (no side effects) | Same eligibility/amount logic as initiation, but never cancels or saves anything; too-close-to-departure returns an *ineligible quote* rather than throwing | `previewRefund_whenFarFromDeparture_…` `:270`, `…withinPartialWindow_…` `:293`, `…tooCloseToDeparture_returnsIneligibleQuoteInsteadOfThrowing` `:311` |
| RF-06 | List refunds for a booking | Newest-first, mapped | `listRefundsForBooking_whenFound_returnsNewestFirstMapped` — `:259` |
| RF-07 | Support/Admin approves a `PENDING` cancellation refund | Marked `PROCESSED`; payment `REFUNDED`; **booking cancelled and seats released at this point** (a reschedule credit instead leaves both the payment `SUCCEEDED` and the booking active) | `processRefund_approve_marksProcessedAndRefundsThePayment` `:377`; `…forRescheduleCredit_leavesPaymentSucceeded` `:383`; e2e `processRefund_approvedBySupportRole_refundsThePaymentAndCancelsTheBooking` — `RefundFlowIT.java:184` |
| RF-08 | Support/Admin rejects a refund | Marked `REJECTED`; **booking stays `CONFIRMED`, seats stay `BOOKED`, payment stays `SUCCEEDED`** — the customer keeps the trip they paid for | `processRefund_reject_marksRejectedAndLeavesPaymentUntouched` — `RefundServiceImplTest.java:430`; e2e `processRefund_rejectedBySupportRole_leavesTheBookingConfirmedAndTravelling` — `RefundFlowIT.java:203` |
| RF-09 | Approve with a fee-waiver override + reason | Waived delta and reason persisted | `processRefund_approveWithOverride_waivesFeeAndRecordsDeltaAndReason` — `:454`; e2e `processRefund_approveWithOverride_persistsWaivedDeltaAndReason` — `RefundFlowIT.java:223` |
| RF-10 | Override amount supplied without a reason, or exceeding the original payment | `RefundOverrideReasonRequiredException` / `RefundOverrideAmountExceedsPaymentException` | `:454`, `:470` |
| RF-11 | Reject with an override amount present | Override is ignored on rejection | `processRefund_rejectWithOverrideAmount_ignoresOverride` — `:512` |
| RF-12 | Process a missing or already-processed refund | `RefundNotFoundException` / `InvalidRefundStateException` | `:507`, `:516` |
| RF-13 | A `CUSTOMER`-role caller attempts to process a refund | Denied by method security (`@PreAuthorize`), not by hand-rolled checks | `processRefund_withCustomerRole_isDeniedByMethodSecurity` — `RefundFlowIT.java:241` |
| RF-14 | `PUT /api/refunds/{id}/process` — no token / valid token / override / negative override amount | 401 without a token; 200 with one; override fields passed through; negative override amount rejected with 400 at the validation layer | `RefundControllerTest.java:62,72,86,102` |
| RF-15 | `POST/GET /api/bookings/{id}/refunds`, `GET .../refund-quote` with a valid token | 200/201 | `initiateRefund_withValidToken_returns201`, `listRefunds_withValidToken_returns200`, `previewRefund_withValidToken_returns200` — `BookingControllerTest.java:185,196,173` |

## 8. Data-integrity safety net (mappers, converters, entity lifecycle)

Not flow scenarios by themselves, but the tests that guarantee the flows above don't silently
corrupt data at the DTO/entity boundary — included for completeness since a coverage audit
should account for every test, not just the business-rule ones:

| Area | What's guaranteed | Test classes |
|---|---|---|
| Booking mapping | Server-controlled fields (id, PNR, status, `idempotencyKey`, `version`) are never overwritten by request DTOs; null-safe on partial input | `BookingMapperTest`, `BookingItemMapperTest` |
| Payment/Refund mapping | Flattened ids (`bookingId`, `paymentId`, `processedById`) are null-safe; failure reason and override fields carry through | `PaymentMapperTest`, `RefundMapperTest` |
| Enum ↔ DB code conversion | Every `BookingStatus`/`PaymentStatus`/`RefundStatus` constant round-trips through its JPA converter; an unknown DB code fails loudly instead of defaulting silently | `BookingStatusConverterTest`, `PaymentStatusConverterTest`, `RefundStatusConverterTest` |
| Entity lifecycle hooks | `Booking.onCreate` defaults `bookingTime` only when unset | `BookingTest` |

---

### How to verify and refresh this mapping

Every case above was produced by reading real `@Test` methods, not by summarizing a class name.
The `file:line` citations **do** go stale as tests are inserted above one another — they were
last verified on 2026-08-03, when 27 of 75 had drifted.

**Verify** (checks that every cited method still exists and that its line number is right):

```bash
cd backend
python - <<'PY'
import re, pathlib
idx = {}
for f in pathlib.Path('src/test/java').rglob('*.java'):
    for i, line in enumerate(f.read_text(encoding='utf-8').splitlines(), 1):
        m = re.search(r'void\s+([a-z]\w+)\s*\(', line)
        if m:
            idx[m.group(1)] = (f.name, i)

doc = pathlib.Path('../docs/functional-test-cases.md').read_text(encoding='utf-8')
bad = 0
for name, _file, line in re.findall(r'`([a-z]\w{8,})`[^`]{0,8}`([A-Za-z0-9]+\.java)?:(\d+)`', doc):
    if name not in idx:
        print(f'ABSENT  {name}'); bad += 1
    elif idx[name][1] != int(line):
        print(f'STALE   {name}: cited :{line}, actual {idx[name][0]}:{idx[name][1]}'); bad += 1
print('OK' if not bad else f'{bad} problem(s)')
PY
```

**Refresh the per-class counts** in the coverage summary from the authoritative source — the
Surefire XML reports, which count parameterized expansions correctly (a plain `grep -c @Test`
undercounts them):

```bash
cd backend && mvn -q clean test
grep -ho 'tests="[0-9]*"' target/surefire-reports/*.xml   # per class
```

Integration-test counts come from `@Test` counts in the `*IT` sources, since Failsafe reports
only exist after `mvn verify` (and none of the ITs are parameterized today).

An **absent** method means a test was renamed or deleted — that needs a human to re-pair the
scenario, not a line-number bump.
