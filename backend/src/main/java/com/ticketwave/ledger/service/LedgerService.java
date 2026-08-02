package com.ticketwave.ledger.service;

import com.ticketwave.ledger.dto.ReconciliationReportResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.Refund;

import java.time.Instant;

public interface LedgerService {

    /**
     * Records a PAYMENT entry for a payment that has just settled as
     * SUCCEEDED. Callers (PaymentServiceImpl) must not call this for
     * PENDING_3DS or FAILED payments — only a settled, successful charge is
     * a real cash movement worth an entry.
     */
    void recordPayment(Payment payment);

    /**
     * Records a REFUND entry for a refund that has just been approved
     * (PROCESSED), at its final amount — including any support/admin
     * override, since the override is already baked into refund.getAmount()
     * by the time processRefund calls this.
     */
    void recordRefund(Refund refund);

    /**
     * Aggregates every ledger entry recorded in [from, to) into per-type
     * totals plus a net cash-movement figure, for admin reconciliation.
     */
    ReconciliationReportResponse reconcile(Instant from, Instant to);
}
