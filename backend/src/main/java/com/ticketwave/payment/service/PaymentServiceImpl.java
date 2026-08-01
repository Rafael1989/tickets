package com.ticketwave.payment.service;

import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.exception.PaymentAmountMismatchException;
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

        Optional<String> declineReason = cardDeclineSimulator.declineReasonFor(request.cardNumber());

        Payment payment;
        try {
            payment = paymentRepository.save(Payment.builder()
                    .booking(booking)
                    .amount(request.amount())
                    .method(request.method())
                    .reference(request.reference())
                    .status(declineReason.isPresent() ? PaymentStatus.FAILED : PaymentStatus.SUCCEEDED)
                    .failureReason(declineReason.orElse(null))
                    .paidAt(declineReason.isPresent() ? null : Instant.now())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // Lost a race to a concurrent request carrying the same reference.
            return paymentMapper.toResponse(paymentRepository.findByReference(request.reference())
                    .orElseThrow(() -> ex));
        }

        if (declineReason.isPresent()) {
            bookingService.failBooking(bookingId);
        } else {
            bookingService.confirmBooking(bookingId);
        }

        return paymentMapper.toResponse(payment);
    }
}
