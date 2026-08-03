package com.ticketwave.payment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardDeclineSimulatorTest {

    private final CardDeclineSimulator simulator = new CardDeclineSimulator();

    @Test
    void declineReasonFor_whenNoCardNumber_approves() {
        assertThat(simulator.declineReasonFor(null)).isEmpty();
    }

    @Test
    void declineReasonFor_whenUnknownCard_approves() {
        assertThat(simulator.declineReasonFor("4242424242424242")).isEmpty();
    }

    @Test
    void declineReasonFor_whenGenericDeclineCard_returnsReason() {
        assertThat(simulator.declineReasonFor("4000000000000002")).contains("Your card was declined.");
    }

    @Test
    void declineReasonFor_whenInsufficientFundsCard_returnsReason() {
        assertThat(simulator.declineReasonFor("4000000000009995")).contains("Insufficient funds.");
    }

    @Test
    void declineReasonFor_whenExpiredCard_returnsReason() {
        assertThat(simulator.declineReasonFor("4000000000000069")).contains("Your card has expired.");
    }

    @Test
    void declineReasonFor_whenCardNumberIsSpaceGrouped_stillMatchesTheDeclineCard() {
        assertThat(simulator.declineReasonFor("4000 0000 0000 0002")).contains("Your card was declined.");
    }

    @Test
    void requiresThreeDs_whenNoCardNumber_returnsFalse() {
        assertThat(simulator.requiresThreeDs(null)).isFalse();
    }

    @Test
    void requiresThreeDs_whenThreeDsTestCard_returnsTrue() {
        assertThat(simulator.requiresThreeDs("4000 0025 0000 3155")).isTrue();
    }

    @Test
    void requiresThreeDs_whenOtherCard_returnsFalse() {
        assertThat(simulator.requiresThreeDs("4242424242424242")).isFalse();
    }
}
