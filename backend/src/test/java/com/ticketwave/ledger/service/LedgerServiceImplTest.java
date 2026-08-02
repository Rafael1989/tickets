package com.ticketwave.ledger.service;

import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.ledger.dto.ReconciliationReportResponse;
import com.ticketwave.ledger.entity.LedgerEntry;
import com.ticketwave.ledger.entity.LedgerEntryType;
import com.ticketwave.ledger.repository.LedgerAggregate;
import com.ticketwave.ledger.repository.LedgerEntryRepository;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.Refund;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private LedgerServiceImpl ledgerService;

    private record TestAggregate(LedgerEntryType entryType, BigDecimal total, long count) implements LedgerAggregate {
        @Override
        public LedgerEntryType getEntryType() {
            return entryType;
        }

        @Override
        public BigDecimal getTotal() {
            return total;
        }

        @Override
        public long getCount() {
            return count;
        }
    }

    private static Booking booking(Schedule schedule) {
        return Booking.builder().id(500L).schedule(schedule).build();
    }

    private static Schedule schedule() {
        Route route = Route.builder().id(1L).build();
        return Schedule.builder().id(10L).route(route).currency("USD").build();
    }

    @Test
    void recordPayment_reFetchesTheBookingAndSavesAPositiveEntry() {
        // payment.getBooking() is deliberately a bare stand-in (only its id
        // is safe to read - see LedgerServiceImpl's Javadoc on why) - the
        // real, fully-loaded booking comes from bookingRepository.findById.
        Schedule schedule = schedule();
        Booking staleBookingRef = Booking.builder().id(500L).build();
        Booking freshBooking = booking(schedule);
        Payment payment = Payment.builder().id(1L).booking(staleBookingRef).amount(new BigDecimal("50.00")).reference("REF-1").build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(freshBooking));

        ledgerService.recordPayment(payment);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        LedgerEntry saved = captor.getValue();
        assertThat(saved.getEntryType()).isEqualTo(LedgerEntryType.PAYMENT);
        assertThat(saved.getBooking()).isEqualTo(freshBooking);
        assertThat(saved.getPayment()).isEqualTo(payment);
        assertThat(saved.getRefund()).isNull();
        assertThat(saved.getAmount()).isEqualByComparingTo("50.00");
        assertThat(saved.getCurrency()).isEqualTo("USD");
    }

    @Test
    void recordPayment_whenBookingMissing_throwsBookingNotFoundException() {
        Booking staleBookingRef = Booking.builder().id(999L).build();
        Payment payment = Payment.builder().id(1L).booking(staleBookingRef).amount(new BigDecimal("50.00")).reference("REF-1").build();
        given(bookingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.recordPayment(payment))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void recordRefund_reFetchesTheBookingAndSavesANegativeEntry() {
        Schedule schedule = schedule();
        Booking staleBookingRef = Booking.builder().id(500L).build();
        Booking freshBooking = booking(schedule);
        Payment payment = Payment.builder().id(1L).booking(staleBookingRef).amount(new BigDecimal("50.00")).build();
        Refund refund = Refund.builder().id(2L).payment(payment).amount(new BigDecimal("30.00")).policyCode("PARTIAL_REFUND").build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(freshBooking));

        ledgerService.recordRefund(refund);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        LedgerEntry saved = captor.getValue();
        assertThat(saved.getEntryType()).isEqualTo(LedgerEntryType.REFUND);
        assertThat(saved.getBooking()).isEqualTo(freshBooking);
        assertThat(saved.getPayment()).isEqualTo(payment);
        assertThat(saved.getRefund()).isEqualTo(refund);
        assertThat(saved.getAmount()).isEqualByComparingTo("-30.00");
        assertThat(saved.getCurrency()).isEqualTo("USD");
    }

    @Test
    void reconcile_aggregatesEachEntryTypeIntoTotalsAndNetAmount() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        given(ledgerEntryRepository.aggregateBetween(from, to)).willReturn(List.of(
                new TestAggregate(LedgerEntryType.PAYMENT, new BigDecimal("500.00"), 5),
                new TestAggregate(LedgerEntryType.REFUND, new BigDecimal("-100.00"), 1)));

        ReconciliationReportResponse report = ledgerService.reconcile(from, to);

        assertThat(report.totalPayments()).isEqualByComparingTo("500.00");
        assertThat(report.paymentCount()).isEqualTo(5);
        assertThat(report.totalRefunds()).isEqualByComparingTo("100.00");
        assertThat(report.refundCount()).isEqualTo(1);
        assertThat(report.totalAdjustments()).isEqualByComparingTo("0");
        assertThat(report.adjustmentCount()).isEqualTo(0);
        assertThat(report.netAmount()).isEqualByComparingTo("400.00");
    }

    @Test
    void reconcile_withNoEntriesInRange_returnsAllZeroes() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        given(ledgerEntryRepository.aggregateBetween(any(), any())).willReturn(List.of());

        ReconciliationReportResponse report = ledgerService.reconcile(from, to);

        assertThat(report.totalPayments()).isEqualByComparingTo("0");
        assertThat(report.totalRefunds()).isEqualByComparingTo("0");
        assertThat(report.totalAdjustments()).isEqualByComparingTo("0");
        assertThat(report.netAmount()).isEqualByComparingTo("0");
    }
}
