package com.ticketwave.auth;

import com.ticketwave.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            new JwtProperties("test-secret-key-at-least-32-bytes-long-for-hmac", 15, 10080));

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
