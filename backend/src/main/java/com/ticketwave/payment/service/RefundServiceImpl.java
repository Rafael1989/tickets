package com.ticketwave.payment.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.payment.dto.RefundDecision;
import com.ticketwave.payment.dto.RefundQuoteResponse;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.entity.Refund;
import com.ticketwave.payment.entity.RefundStatus;
import com.ticketwave.payment.exception.CancellationNotAllowedException;
import com.ticketwave.payment.exception.InvalidRefundStateException;
import com.ticketwave.payment.exception.PaymentNotFoundException;
import com.ticketwave.payment.exception.RefundNotFoundException;
import com.ticketwave.payment.mapper.RefundMapper;
import com.ticketwave.payment.repository.PaymentRepository;
import com.ticketwave.payment.repository.RefundRepository;
import com.ticketwave.payment.service.RefundPolicyService.RefundQuote;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingService bookingService;
    private final RefundPolicyService refundPolicyService;
    private final RefundMapper refundMapper;
    private final AuditService auditService;

    public RefundServiceImpl(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            BookingService bookingService,
            RefundPolicyService refundPolicyService,
            RefundMapper refundMapper,
            AuditService auditService
    ) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.bookingService = bookingService;
        this.refundPolicyService = refundPolicyService;
        this.refundMapper = refundMapper;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN') or @bookingOwnership.isOwnedBy(#bookingId, authentication.name)")
    @Transactional(readOnly = true)
    public RefundQuoteResponse previewRefund(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(bookingId, booking.getStatus(), BookingStatus.CANCELLED);
        }

        Payment payment = paymentRepository.findByBookingId(bookingId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow(() -> new PaymentNotFoundException(bookingId));

        Duration untilDeparture = Duration.between(Instant.now(), booking.getSchedule().getDepartureTime());
        Optional<RefundQuote> quote = refundPolicyService.quoteFor(untilDeparture);

        if (quote.isEmpty()) {
            return new RefundQuoteResponse(bookingId, payment.getAmount(), null, null,
                    BigDecimal.ZERO, payment.getAmount(), payment.getMethod(), false);
        }

        RefundQuote q = quote.get();
        BigDecimal refundAmount = payment.getAmount().multiply(q.rate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nonRefundable = payment.getAmount().subtract(refundAmount);
        return new RefundQuoteResponse(bookingId, payment.getAmount(), q.policyCode(), q.rate(),
                refundAmount, nonRefundable, payment.getMethod(), true);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN') or @bookingOwnership.isOwnedBy(#bookingId, authentication.name)")
    @Transactional
    public RefundResponse initiateRefund(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(bookingId, booking.getStatus(), BookingStatus.CANCELLED);
        }

        Payment payment = paymentRepository.findByBookingId(bookingId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow(() -> new PaymentNotFoundException(bookingId));

        RefundQuote quote = calculateRefundQuote(bookingId, booking.getSchedule());
        BigDecimal refundAmount = payment.getAmount()
                .multiply(quote.rate())
                .setScale(2, RoundingMode.HALF_UP);

        Refund refund = refundRepository.save(Refund.builder()
                .payment(payment)
                .amount(refundAmount)
                .policyCode(quote.policyCode())
                .status(RefundStatus.PENDING)
                .build());

        // Cancelling here (not before) means a policy rejection above never
        // touches the booking/seats at all.
        bookingService.cancelBooking(bookingId);

        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.record(actor, "REFUND_INITIATED", "REFUND", refund.getId(),
                "bookingId=" + bookingId + " policy=" + quote.policyCode() + " amount=" + refundAmount);

        return refundMapper.toResponse(refund);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    @Transactional
    public RefundResponse processRefund(Long refundId, String processedByUsername, RefundDecision decision) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new RefundNotFoundException(refundId));

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new InvalidRefundStateException(refundId, refund.getStatus());
        }

        User processedBy = userRepository.findByUsername(processedByUsername)
                .orElseThrow(() -> new UserNotFoundException(processedByUsername));

        refund.setStatus(decision == RefundDecision.APPROVE ? RefundStatus.PROCESSED : RefundStatus.REJECTED);
        refund.setProcessedBy(processedBy);
        refund.setProcessedAt(Instant.now());

        // A RESCHEDULE_CREDIT refund settles a fare-difference credit on a
        // booking that's still CONFIRMED and travelling - the payment still
        // backs an active booking, just at its (already-reduced) net amount,
        // so it must stay SUCCEEDED. Only a cancellation refund (FULL/PARTIAL)
        // means the booking's payment has actually been given back.
        boolean isCancellationRefund = RefundPolicyService.FULL_REFUND_POLICY.equals(refund.getPolicyCode())
                || RefundPolicyService.PARTIAL_REFUND_POLICY.equals(refund.getPolicyCode());
        if (decision == RefundDecision.APPROVE && isCancellationRefund) {
            refund.getPayment().setStatus(PaymentStatus.REFUNDED);
        }

        String action = decision == RefundDecision.APPROVE ? "REFUND_APPROVED" : "REFUND_REJECTED";
        auditService.record(processedBy.getUsername(), action, "REFUND", refundId, "status=" + refund.getStatus());

        return refundMapper.toResponse(refund);
    }

    /**
     * >= fullRefundThresholdDays out: 100% refund. >= partialRefundThresholdHours
     * (but under the full-refund window): partialRefundRate. Any closer than
     * that: cancellation is blocked entirely, not just refund-free — this
     * booking's schedule is departing too soon to touch.
     */
    private RefundQuote calculateRefundQuote(Long bookingId, Schedule schedule) {
        Duration untilDeparture = Duration.between(Instant.now(), schedule.getDepartureTime());
        return refundPolicyService.quoteFor(untilDeparture)
                .orElseThrow(() -> new CancellationNotAllowedException(bookingId, schedule.getDepartureTime()));
    }
}
