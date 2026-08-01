package com.ticketwave.payment.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.RescheduleQuoteResponse;
import com.ticketwave.booking.dto.RescheduleRequest;
import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.entity.Refund;
import com.ticketwave.payment.entity.RefundStatus;
import com.ticketwave.payment.exception.CancellationNotAllowedException;
import com.ticketwave.payment.exception.FareDifferenceDeclinedException;
import com.ticketwave.payment.exception.FareDifferencePaymentRequiredException;
import com.ticketwave.payment.exception.PaymentNotFoundException;
import com.ticketwave.payment.repository.PaymentRepository;
import com.ticketwave.payment.repository.RefundRepository;
import com.ticketwave.pricing.dto.PromoCodeApplication;
import com.ticketwave.pricing.service.PricingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RescheduleServiceImpl implements RescheduleService {

    static final String RESCHEDULE_CREDIT_POLICY = "RESCHEDULE_CREDIT";

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final PricingService pricingService;
    private final RefundPolicyService refundPolicyService;
    private final CardDeclineSimulator cardDeclineSimulator;
    private final BookingService bookingService;
    private final AuditService auditService;

    public RescheduleServiceImpl(
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            ScheduleRepository scheduleRepository,
            SeatRepository seatRepository,
            PricingService pricingService,
            RefundPolicyService refundPolicyService,
            CardDeclineSimulator cardDeclineSimulator,
            BookingService bookingService,
            AuditService auditService
    ) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.scheduleRepository = scheduleRepository;
        this.seatRepository = seatRepository;
        this.pricingService = pricingService;
        this.refundPolicyService = refundPolicyService;
        this.cardDeclineSimulator = cardDeclineSimulator;
        this.bookingService = bookingService;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN') or @bookingOwnership.isOwnedBy(#bookingId, authentication.name)")
    @Transactional(readOnly = true)
    public RescheduleQuoteResponse previewReschedule(Long bookingId, Long scheduleId, List<Long> seatIds) {
        Booking booking = getBookingOrThrow(bookingId);
        requireReschedulable(booking);

        BigDecimal newTotal = computeNewTotal(booking, scheduleId, seatIds);
        BigDecimal fareDifference = newTotal.subtract(booking.getTotalAmount());

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            // INITIATED: free to reschedule regardless of the fare delta, no
            // departure-proximity window applies to an unpaid booking.
            return new RescheduleQuoteResponse(bookingId, booking.getTotalAmount(), newTotal, fareDifference, true, false);
        }

        boolean eligible = isEligible(booking);
        boolean paymentRequired = eligible && fareDifference.signum() > 0;
        return new RescheduleQuoteResponse(bookingId, booking.getTotalAmount(), newTotal, fareDifference, eligible, paymentRequired);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN') or @bookingOwnership.isOwnedBy(#bookingId, authentication.name)")
    @Transactional
    public BookingDetailResponse reschedule(Long bookingId, RescheduleRequest request) {
        Booking booking = getBookingOrThrow(bookingId);
        requireReschedulable(booking);

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return bookingService.rescheduleBooking(bookingId, request);
        }

        if (!isEligible(booking)) {
            throw new CancellationNotAllowedException(bookingId, booking.getSchedule().getDepartureTime());
        }

        Payment payment = paymentRepository.findByBookingId(bookingId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow(() -> new PaymentNotFoundException(bookingId));

        BigDecimal oldTotal = booking.getTotalAmount();
        // Perform the swap first, inside this same transaction: if the
        // fare-difference charge below is required-but-missing or declined,
        // throwing rolls the whole thing back (seats, schedule, total)
        // together with it - nothing is left half-applied.
        BookingDetailResponse result = bookingService.rescheduleBooking(bookingId, request);
        BigDecimal difference = result.booking().totalAmount().subtract(oldTotal);

        if (difference.signum() > 0) {
            collectFareDifference(bookingId, payment, difference, request);
        } else if (difference.signum() < 0) {
            issueRescheduleCredit(bookingId, payment, difference.abs());
        }

        return result;
    }

    private void collectFareDifference(Long bookingId, Payment payment, BigDecimal difference, RescheduleRequest request) {
        if (isBlank(request.paymentMethod()) || isBlank(request.paymentReference())) {
            throw new FareDifferencePaymentRequiredException(bookingId, difference);
        }

        Optional<String> declineReason = cardDeclineSimulator.declineReasonFor(request.cardNumber());
        if (declineReason.isPresent()) {
            throw new FareDifferenceDeclinedException(bookingId, declineReason.get());
        }

        // Adjusts the existing payment's amount rather than inserting a
        // second Payment row, so it stays the single source of truth this
        // booking's later cancellation math (initiateRefund) already relies
        // on via findByBookingId(...).findFirst().
        payment.setAmount(payment.getAmount().add(difference));

        auditService.record(currentUsername(), "RESCHEDULE_FARE_COLLECTED", "PAYMENT", payment.getId(),
                "bookingId=" + bookingId + " amount=" + difference);
    }

    private void issueRescheduleCredit(Long bookingId, Payment payment, BigDecimal creditAmount) {
        payment.setAmount(payment.getAmount().subtract(creditAmount));

        Refund refund = refundRepository.save(Refund.builder()
                .payment(payment)
                .amount(creditAmount)
                .policyCode(RESCHEDULE_CREDIT_POLICY)
                .status(RefundStatus.PENDING)
                .build());

        auditService.record(currentUsername(), "RESCHEDULE_CREDIT_ISSUED", "REFUND", refund.getId(),
                "bookingId=" + bookingId + " amount=" + creditAmount);
    }

    private BigDecimal computeNewTotal(Booking booking, Long scheduleId, List<Long> seatIds) {
        Schedule newSchedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));

        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()) {
            Set<Long> foundIds = seats.stream().map(Seat::getId).collect(Collectors.toSet());
            Long missingId = seatIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
            throw new SeatNotFoundException(missingId);
        }

        BigDecimal subtotal = seats.stream()
                .map(seat -> pricingService.calculateSeatFare(newSchedule, seat))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (booking.getPromoCode() == null) {
            return subtotal;
        }
        PromoCodeApplication application = pricingService.previewPromoCode(booking.getPromoCode().getCode(), subtotal);
        return subtotal.subtract(application.discountAmount());
    }

    private boolean isEligible(Booking booking) {
        Duration untilDeparture = Duration.between(Instant.now(), booking.getSchedule().getDepartureTime());
        return refundPolicyService.quoteFor(untilDeparture).isPresent();
    }

    private void requireReschedulable(Booking booking) {
        if (booking.getStatus() != BookingStatus.INITIATED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(booking.getId(), booking.getStatus(), BookingStatus.INITIATED);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private Booking getBookingOrThrow(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }
}
