package com.ticketwave.payment.service;

import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.exception.InvalidBookingStateException;
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

/**
 * Deliberately has no method-level @Transactional spanning the whole flow:
 * the payment insert and the booking confirmation are two independently
 * transactional steps (each repository call, and BookingService.confirmBooking,
 * already has its own boundary). This matters for idempotency — if the insert
 * hits the reference's UNIQUE constraint, only that one operation's
 * transaction rolls back, so the recovery read just afterward runs cleanly
 * instead of hitting "transaction aborted" against a poisoned outer
 * transaction (PostgreSQL aborts the whole transaction on a constraint
 * violation, not just the failed statement). The tradeoff is a small
 * eventual-consistency window if the process dies between the two steps
 * (payment recorded, booking not yet confirmed) — acceptable for now, and
 * a reconciliation job is the right place to close that gap later, not this
 * method.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentMapper paymentMapper;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            BookingService bookingService,
            PaymentMapper paymentMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
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

        if (booking.getStatus() != BookingStatus.INITIATED) {
            throw new InvalidBookingStateException(bookingId, booking.getStatus(), BookingStatus.CONFIRMED);
        }

        if (request.amount().compareTo(booking.getTotalAmount()) != 0) {
            throw new PaymentAmountMismatchException(bookingId, booking.getTotalAmount(), request.amount());
        }

        Payment payment;
        try {
            payment = paymentRepository.save(Payment.builder()
                    .booking(booking)
                    .amount(request.amount())
                    .method(request.method())
                    .reference(request.reference())
                    .status(PaymentStatus.SUCCEEDED)
                    .paidAt(Instant.now())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // Lost a race to a concurrent request carrying the same reference.
            return paymentMapper.toResponse(paymentRepository.findByReference(request.reference())
                    .orElseThrow(() -> ex));
        }

        bookingService.confirmBooking(bookingId);

        return paymentMapper.toResponse(payment);
    }
}
