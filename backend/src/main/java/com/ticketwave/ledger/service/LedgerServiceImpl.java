package com.ticketwave.ledger.service;

import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.ledger.dto.ReconciliationReportResponse;
import com.ticketwave.ledger.entity.LedgerEntry;
import com.ticketwave.ledger.entity.LedgerEntryType;
import com.ticketwave.ledger.repository.LedgerAggregate;
import com.ticketwave.ledger.repository.LedgerEntryRepository;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.Refund;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class LedgerServiceImpl implements LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final BookingRepository bookingRepository;

    public LedgerServiceImpl(LedgerEntryRepository ledgerEntryRepository, BookingRepository bookingRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Re-fetches the booking (and, through it, the schedule/currency)
     * within this method's own transaction rather than trusting payment's
     * already-attached booking/schedule: with open-in-view disabled, both
     * were loaded in an earlier, now-closed transactional step (see
     * PaymentServiceImpl's own Javadoc on why it has no single spanning
     * transaction), so their lazy associations have no session left to
     * initialize from — accessing payment.getBooking().getSchedule()
     * directly here throws LazyInitializationException. Only payment's own
     * scalar fields (id, amount, reference) and payment.getBooking().getId()
     * (safe on an uninitialized proxy — an id never triggers a fetch) are
     * safe to read from the passed-in entities.
     */
    @Override
    @Transactional
    public void recordPayment(Payment payment) {
        Booking booking = bookingRepository.findById(payment.getBooking().getId())
                .orElseThrow(() -> new BookingNotFoundException(payment.getBooking().getId()));
        ledgerEntryRepository.save(LedgerEntry.builder()
                .booking(booking)
                .payment(payment)
                .entryType(LedgerEntryType.PAYMENT)
                .amount(payment.getAmount())
                .currency(booking.getSchedule().getCurrency())
                .description("Payment " + payment.getReference())
                .build());
    }

    @Override
    @Transactional
    public void recordRefund(Refund refund) {
        Payment payment = refund.getPayment();
        Booking booking = bookingRepository.findById(payment.getBooking().getId())
                .orElseThrow(() -> new BookingNotFoundException(payment.getBooking().getId()));
        ledgerEntryRepository.save(LedgerEntry.builder()
                .booking(booking)
                .payment(payment)
                .refund(refund)
                .entryType(LedgerEntryType.REFUND)
                .amount(refund.getAmount().negate())
                .currency(booking.getSchedule().getCurrency())
                .description("Refund policy=" + refund.getPolicyCode())
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ReconciliationReportResponse reconcile(Instant from, Instant to) {
        List<LedgerAggregate> aggregates = ledgerEntryRepository.aggregateBetween(from, to);
        Map<LedgerEntryType, LedgerAggregate> byType = new EnumMap<>(LedgerEntryType.class);
        for (LedgerAggregate aggregate : aggregates) {
            byType.put(aggregate.getEntryType(), aggregate);
        }

        BigDecimal totalPayments = totalFor(byType, LedgerEntryType.PAYMENT);
        BigDecimal totalRefunds = totalFor(byType, LedgerEntryType.REFUND);
        BigDecimal totalAdjustments = totalFor(byType, LedgerEntryType.ADJUSTMENT);
        BigDecimal netAmount = totalPayments.add(totalRefunds).add(totalAdjustments);

        return new ReconciliationReportResponse(
                from, to,
                totalPayments, countFor(byType, LedgerEntryType.PAYMENT),
                totalRefunds.abs(), countFor(byType, LedgerEntryType.REFUND),
                totalAdjustments.abs(), countFor(byType, LedgerEntryType.ADJUSTMENT),
                netAmount
        );
    }

    private static BigDecimal totalFor(Map<LedgerEntryType, LedgerAggregate> byType, LedgerEntryType type) {
        LedgerAggregate aggregate = byType.get(type);
        return aggregate == null ? BigDecimal.ZERO : aggregate.getTotal();
    }

    private static long countFor(Map<LedgerEntryType, LedgerAggregate> byType, LedgerEntryType type) {
        LedgerAggregate aggregate = byType.get(type);
        return aggregate == null ? 0L : aggregate.getCount();
    }
}
