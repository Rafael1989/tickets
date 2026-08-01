package com.ticketwave.payment.service;

import com.ticketwave.config.RefundProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * The shared cancellation/reschedule eligibility window: full refund far
 * enough out, a prorated partial refund closer in, and blocked entirely once
 * departure is imminent. Used both to quote/settle a cancellation refund and
 * to gate whether a CONFIRMED (paid) booking may still be rescheduled at
 * all — a booking too close to departure to cancel is too close to
 * reschedule for the same reason.
 */
@Component
public class RefundPolicyService {

    public static final String FULL_REFUND_POLICY = "FULL_REFUND";
    public static final String PARTIAL_REFUND_POLICY = "PARTIAL_REFUND";

    private final RefundProperties refundProperties;

    public RefundPolicyService(RefundProperties refundProperties) {
        this.refundProperties = refundProperties;
    }

    /**
     * @return empty if departure has already passed or is too imminent
     * (cancellation/reschedule blocked); otherwise the applicable rate and
     * policy code.
     */
    public Optional<RefundQuote> quoteFor(Duration untilDeparture) {
        if (untilDeparture.isNegative()) {
            return Optional.empty();
        }
        if (untilDeparture.toDays() >= refundProperties.fullRefundThresholdDays()) {
            return Optional.of(new RefundQuote(BigDecimal.ONE, FULL_REFUND_POLICY));
        }
        if (untilDeparture.toHours() >= refundProperties.partialRefundThresholdHours()) {
            return Optional.of(new RefundQuote(refundProperties.partialRefundRate(), PARTIAL_REFUND_POLICY));
        }
        return Optional.empty();
    }

    public record RefundQuote(BigDecimal rate, String policyCode) {
    }
}
