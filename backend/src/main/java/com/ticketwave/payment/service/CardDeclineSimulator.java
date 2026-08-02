package com.ticketwave.payment.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Stands in for a real payment gateway's approve/decline decision. There is
 * no PSP integration in this stack (no Stripe/Redis/queue), so outcomes are
 * decided from a handful of well-known Stripe test PANs — the same numbers
 * Stripe's own test mode uses, so the checkout UI can point demo users at
 * real, recognizable "these decline" test cards instead of an arbitrary rule.
 * Card numbers are only ever read here, in memory, to make this decision —
 * never persisted.
 */
@Component
public class CardDeclineSimulator {

    private static final Map<String, String> DECLINE_REASONS = Map.of(
            "4000000000000002", "Your card was declined.",
            "4000000000009995", "Insufficient funds.",
            "4000000000000069", "Your card has expired."
    );

    /** The same PAN Stripe's own test mode uses for "requires 3D Secure authentication". */
    private static final String THREE_DS_REQUIRED_CARD = "4000002500003155";

    /**
     * @return the decline reason, if this card number (or absence of one,
     * e.g. for non-card methods) should be declined; empty if it should be
     * approved.
     */
    public Optional<String> declineReasonFor(String cardNumber) {
        String normalized = normalize(cardNumber);
        return normalized == null ? Optional.empty() : Optional.ofNullable(DECLINE_REASONS.get(normalized));
    }

    /** @return true if this card number should be routed through a 3D Secure challenge before it can be decided. */
    public boolean requiresThreeDs(String cardNumber) {
        return THREE_DS_REQUIRED_CARD.equals(normalize(cardNumber));
    }

    private static String normalize(String cardNumber) {
        return cardNumber == null ? null : cardNumber.replace(" ", "");
    }
}
