package com.ticketwave.payment.service;

import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.ledger.service.LedgerService;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.exception.InvalidPaymentStateException;
import com.ticketwave.payment.exception.PaymentAmountMismatchException;
import com.ticketwave.payment.exception.PaymentNotFoundException;
import com.ticketwave.payment.mapper.PaymentMapper;
import com.ticketwave.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Deliberately has no method-level @Transactional spanning the whole flow:
 * the payment insert and the booking state transition are independently
 * transactional steps (each repository call, and BookingService's own
 * methods, already have their own boundary). This matters for idempotency —
 * if the insert hits the reference's UNIQUE constraint, only that one
 * operation's transaction rolls back, so the recovery read just afterward
 * runs cleanly instead of hitting "transaction aborted" against a poisoned
 * outer transaction (PostgreSQL aborts the whole transaction on a constraint
 * violation, not just the failed statement). The tradeoff is a small
 * eventual-consistency window if the process dies between steps (e.g.
 * booking left at PAYMENT_PROCESSING with no payment row) — acceptable for
 * now, and a reconciliation job is the right place to close that gap later,
 * not this method.
 *
 * There is no real payment gateway behind this (no Stripe/PSP integration in
 * this stack) — CardDeclineSimulator stands in for one, deciding
 * approve/decline from a card number that's never persisted. The whole
 * decision is made synchronously within this one request, so
 * PAYMENT_PROCESSING never actually sits open across requests, which is why
 * there's no need to guard against the seat hold expiring mid-flight.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    /** The simulated 3DS challenge's one and only "correct" code — anything else fails it, same as a real OTP mismatch. */
    private static final String THREE_DS_VALID_CODE = "123456";

    /**
     * Two requests carrying the same reference both pass the pre-check
     * (see recordPayment's own comment on that race) and both call
     * markPaymentProcessing on the same booking row concurrently: one's
     * commit wins, bumping the @Version column; the other's commit then
     * fails with ObjectOptimisticLockingFailureException, since the row it
     * read is now stale. 3 attempts, not 1 retry: under real concurrency a
     * third overlapping writer is possible, if unlikely, and a fresh read
     * each attempt makes a retry cheap and safe to repeat.
     */
    private static final int MAX_MARK_PROCESSING_ATTEMPTS = 3;

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final CardDeclineSimulator cardDeclineSimulator;
    private final PaymentMapper paymentMapper;
    private final LedgerService ledgerService;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            BookingService bookingService,
            CardDeclineSimulator cardDeclineSimulator,
            PaymentMapper paymentMapper,
            LedgerService ledgerService
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.cardDeclineSimulator = cardDeclineSimulator;
        this.paymentMapper = paymentMapper;
        this.ledgerService = ledgerService;
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN') or @bookingOwnership.isOwnedBy(#bookingId, authentication.name)")
    public PaymentResponse recordPayment(Long bookingId, PaymentRequest request) {
        var existing = paymentRepository.findByReference(request.reference());
        if (existing.isPresent()) {
            return paymentMapper.toResponse(existing.get());
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        // Checked before markPaymentProcessing so a mismatched request never
        // leaves the booking stuck in PAYMENT_PROCESSING with no payment
        // attempt actually underway to resolve it.
        if (request.amount().compareTo(booking.getTotalAmount()) != 0) {
            throw new PaymentAmountMismatchException(bookingId, booking.getTotalAmount(), request.amount());
        }

        // Also what stops a stray payment attempt from reopening a booking
        // that's already been settled: throws InvalidBookingStateException
        // if the booking is CONFIRMED or CANCELLED. A concurrent request
        // racing this same in-flight attempt (e.g. the same-reference retry
        // below) re-affirms PAYMENT_PROCESSING rather than erroring - unless
        // the racing request already ran the entire flow to completion
        // (payment saved + booking CONFIRMED) between this method's
        // findByReference check above and this call. That's not a real
        // conflict, just this method losing the race entirely rather than
        // partway through, so recover the winner's payment instead of
        // surfacing InvalidBookingStateException to what is, from the
        // caller's perspective, an idempotent retry.
        try {
            markPaymentProcessingWithRetry(bookingId);
        } catch (InvalidBookingStateException ex) {
            return paymentRepository.findByReference(request.reference())
                    .map(paymentMapper::toResponse)
                    .orElseThrow(() -> ex);
        }

        boolean requiresThreeDs = cardDeclineSimulator.requiresThreeDs(request.cardNumber());
        Optional<String> declineReason = requiresThreeDs
                ? Optional.empty()
                : cardDeclineSimulator.declineReasonFor(request.cardNumber());

        Payment payment;
        try {
            payment = paymentRepository.save(Payment.builder()
                    .booking(booking)
                    .amount(request.amount())
                    .method(request.method())
                    .reference(request.reference())
                    .status(statusFor(requiresThreeDs, declineReason))
                    .failureReason(declineReason.orElse(null))
                    .paidAt(!requiresThreeDs && declineReason.isEmpty() ? Instant.now() : null)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // Lost a race to a concurrent request carrying the same reference.
            return paymentMapper.toResponse(paymentRepository.findByReference(request.reference())
                    .orElseThrow(() -> ex));
        }

        // A 3DS-pending payment leaves the booking in PAYMENT_PROCESSING on
        // purpose - it's neither confirmed nor failed until the challenge
        // (confirmThreeDs) resolves it one way or the other.
        if (!requiresThreeDs) {
            if (declineReason.isPresent()) {
                bookingService.failBooking(bookingId);
            } else {
                bookingService.confirmBooking(bookingId);
                recordLedgerPaymentSafely(payment);
            }
        }

        return paymentMapper.toResponse(payment);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN') or @bookingOwnership.isOwnedBy(#bookingId, authentication.name)")
    public PaymentResponse confirmThreeDs(Long bookingId, Long paymentId, String code) {
        Payment payment = paymentRepository.findById(paymentId)
                .filter(candidate -> candidate.getBooking().getId().equals(bookingId))
                .orElseThrow(() -> PaymentNotFoundException.byId(paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING_3DS) {
            throw new InvalidPaymentStateException(paymentId, payment.getStatus(), PaymentStatus.PENDING_3DS);
        }

        // findById above already ran (and closed) its own transaction — with open-in-view
        // disabled, `payment` is detached by this point, so the setters below are inert unless
        // explicitly saved. Without this, confirmThreeDs would return a response that *looks*
        // SUCCEEDED/FAILED while the persisted row stays PENDING_3DS forever (the exact bug this
        // regression test set exists to catch).
        if (THREE_DS_VALID_CODE.equals(code)) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setPaidAt(Instant.now());
            payment = paymentRepository.save(payment);
            bookingService.confirmBooking(bookingId);
            recordLedgerPaymentSafely(payment);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("3D Secure authentication failed.");
            payment = paymentRepository.save(payment);
            bookingService.failBooking(bookingId);
        }

        return paymentMapper.toResponse(payment);
    }

    /**
     * The payment is already SUCCEEDED and the booking already CONFIRMED by the time this runs
     * (see this class's own Javadoc on why there's no spanning transaction) — the ledger entry is
     * an append-only audit/reconciliation record of that already-committed fact, not something the
     * customer's payment success depends on. Letting a ledger failure (e.g. a DB permissions or
     * connectivity blip) propagate would turn a successful charge into a raw 500 for the customer,
     * hiding that their payment actually went through. Logged at ERROR rather than swallowed
     * silently, since a missing ledger entry is a real reconciliation gap someone needs to backfill.
     */
    private void recordLedgerPaymentSafely(Payment payment) {
        try {
            ledgerService.recordPayment(payment);
        } catch (RuntimeException ex) {
            log.error("Failed to record ledger entry for payment {} (booking {}) — payment succeeded regardless",
                    payment.getId(), payment.getBooking().getId(), ex);
        }
    }

    /**
     * Retries through bookingService (a different Spring bean, so each call
     * genuinely goes through its transactional proxy and re-reads the
     * booking fresh) rather than inside BookingServiceImpl itself — a
     * @Transactional method can't usefully catch-and-retry its own failed
     * commit from within, since the transaction is already gone by the time
     * the exception reaches the caller.
     */
    private void markPaymentProcessingWithRetry(Long bookingId) {
        // Note for coverage readers: the loop's exit-by-condition branch is
        // unreachable by construction — every iteration either returns or,
        // on the final attempt, rethrows. It is the one permanently
        // uncovered branch outside the @PrePersist hooks (see docs/testing.md).
        for (int attempt = 1; attempt <= MAX_MARK_PROCESSING_ATTEMPTS; attempt++) {
            try {
                bookingService.markPaymentProcessing(bookingId);
                return;
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (attempt == MAX_MARK_PROCESSING_ATTEMPTS) {
                    throw ex;
                }
            }
        }
    }

    private static PaymentStatus statusFor(boolean requiresThreeDs, Optional<String> declineReason) {
        if (requiresThreeDs) {
            return PaymentStatus.PENDING_3DS;
        }
        return declineReason.isPresent() ? PaymentStatus.FAILED : PaymentStatus.SUCCEEDED;
    }
}
