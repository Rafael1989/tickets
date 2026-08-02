package com.ticketwave.payment.service;

import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.exception.InvalidPaymentStateException;
import com.ticketwave.payment.exception.PaymentAmountMismatchException;
import com.ticketwave.payment.exception.PaymentNotFoundException;
import com.ticketwave.payment.mapper.PaymentMapper;
import com.ticketwave.payment.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
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

    /** The simulated 3DS challenge's one and only "correct" code — anything else fails it, same as a real OTP mismatch. */
    private static final String THREE_DS_VALID_CODE = "123456";

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final CardDeclineSimulator cardDeclineSimulator;
    private final PaymentMapper paymentMapper;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            BookingService bookingService,
            CardDeclineSimulator cardDeclineSimulator,
            PaymentMapper paymentMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.cardDeclineSimulator = cardDeclineSimulator;
        this.paymentMapper = paymentMapper;
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
        // below) re-affirms PAYMENT_PROCESSING rather than erroring.
        bookingService.markPaymentProcessing(bookingId);

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

        if (THREE_DS_VALID_CODE.equals(code)) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setPaidAt(Instant.now());
            bookingService.confirmBooking(bookingId);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("3D Secure authentication failed.");
            bookingService.failBooking(bookingId);
        }

        return paymentMapper.toResponse(payment);
    }

    private static PaymentStatus statusFor(boolean requiresThreeDs, Optional<String> declineReason) {
        if (requiresThreeDs) {
            return PaymentStatus.PENDING_3DS;
        }
        return declineReason.isPresent() ? PaymentStatus.FAILED : PaymentStatus.SUCCEEDED;
    }
}
