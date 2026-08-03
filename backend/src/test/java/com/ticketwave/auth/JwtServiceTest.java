package com.ticketwave.auth;

import com.ticketwave.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            new JwtProperties("test-secret-key-at-least-32-bytes-long-for-hmac", 15, 10080));

    @Test
    void constructor_whenSecretIsShorterThan32Bytes_failsNamingTheVariableAndTheRequirement() {
        // Regression test. A 13-character JWT_SECRET used to surface as jjwt's
        // WeakKeyException quoting RFC 7518 — correct, but it never names
        // JWT_SECRET, so the reader learns the algorithm is unhappy without
        // learning which variable to change.
        JwtProperties tooShort = new JwtProperties("short-secret", 15, 10080);

        assertThatThrownBy(() -> new JwtService(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("at least 32")
                .hasMessageContaining("but is 12")
                // The key itself must never reach a log, and a startup failure
                // message is precisely what gets logged.
                .hasMessageNotContaining("short-secret");
    }

    @Test
    void generateAccessToken_thenExtractSubjectAndRoles_roundTrips() {
        String token = jwtService.generateAccessToken("alice", List.of("CUSTOMER", "SUPPORT"));

        assertThat(jwtService.extractSubject(token)).isEqualTo("alice");
        assertThat(jwtService.extractRoles(token)).containsExactly("CUSTOMER", "SUPPORT");
    }

    @Test
    void getAccessTokenTtlSeconds_convertsConfiguredMinutesToSeconds() {
        assertThat(jwtService.getAccessTokenTtlSeconds()).isEqualTo(15 * 60L);
    }

    @Test
    void isValid_forATokenItIssued_returnsTrue() {
        String token = jwtService.generateAccessToken("alice", List.of("CUSTOMER"));

        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void isValid_forATamperedOrMalformedToken_returnsFalse() {
        assertThat(jwtService.isValid("not-a-real-jwt-at-all")).isFalse();
    }

    @Test
    void isValid_forATokenSignedWithADifferentSecret_returnsFalse() {
        JwtService otherService = new JwtService(
                new JwtProperties("a-completely-different-secret-key-32-bytes!", 15, 10080));
        String token = otherService.generateAccessToken("alice", List.of("CUSTOMER"));

        assertThat(jwtService.isValid(token)).isFalse();
    }
}
