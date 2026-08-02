package com.ticketwave.ratelimit;

import com.ticketwave.auth.JwtService;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    private static final JwtService JWT_SERVICE =
            new JwtService(new JwtProperties("test-only-secret-key-at-least-32-bytes-long", 15, 10080));

    private static RateLimiter newLimiter(int requestsPerWindow) {
        return new RateLimiter(new RateLimitProperties(requestsPerWindow, 60), Clock.fixed(Instant.now(), ZoneOffset.UTC));
    }

    private static void performRequest(RateLimitingFilter filter, MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void matchingPath_underLimit_passesThroughEveryTime() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(2), List.of("/api/search"), List.of("/api/partner/**"), JWT_SERVICE, 60);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            performRequest(filter, request, response);

            assertThat(response.getStatus()).isEqualTo(200); // MockFilterChain defaults to 200 when reached
        }
    }

    @Test
    void matchingPath_overLimit_returns429WithRetryAfterAndJsonBody() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(1), List.of("/api/search"), List.of("/api/partner/**"), JWT_SERVICE, 60);

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/search");
        first.setRemoteAddr("10.0.0.2");
        performRequest(filter, first, new MockHttpServletResponse());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/search");
        second.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        performRequest(filter, second, secondResponse);

        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(secondResponse.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void differentClientIps_areRateLimitedIndependently() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(1), List.of("/api/search"), List.of("/api/partner/**"), JWT_SERVICE, 60);

        MockHttpServletRequest clientA1 = new MockHttpServletRequest("GET", "/api/search");
        clientA1.setRemoteAddr("10.0.0.3");
        performRequest(filter, clientA1, new MockHttpServletResponse());

        MockHttpServletRequest clientA2 = new MockHttpServletRequest("GET", "/api/search");
        clientA2.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse clientA2Response = new MockHttpServletResponse();
        performRequest(filter, clientA2, clientA2Response);
        assertThat(clientA2Response.getStatus()).isEqualTo(429); // client A already exhausted its own limit

        MockHttpServletRequest clientB1 = new MockHttpServletRequest("GET", "/api/search");
        clientB1.setRemoteAddr("10.0.0.4");
        MockHttpServletResponse clientB1Response = new MockHttpServletResponse();
        performRequest(filter, clientB1, clientB1Response);
        assertThat(clientB1Response.getStatus()).isEqualTo(200); // unaffected by client A's usage
    }

    @Test
    void matchingPath_withNonEmptyContextPath_stripsContextPathBeforeMatching() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(1), List.of("/api/search"), List.of("/api/partner/**"), JWT_SERVICE, 60);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ticketwave/api/search");
        request.setContextPath("/ticketwave");
        request.setRemoteAddr("10.0.0.6");
        MockHttpServletResponse first = new MockHttpServletResponse();
        performRequest(filter, request, first);
        assertThat(first.getStatus()).isEqualTo(200);

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/ticketwave/api/search");
        second.setContextPath("/ticketwave");
        second.setRemoteAddr("10.0.0.6");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        performRequest(filter, second, secondResponse);
        assertThat(secondResponse.getStatus()).isEqualTo(429); // proves the pattern matched after stripping the context path
    }

    @Test
    void matchingPath_withNullContextPath_stillMatchesOnTheRawUri() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(1), List.of("/api/search"), List.of("/api/partner/**"), JWT_SERVICE, 60);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search");
        request.setContextPath(null);
        request.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse first = new MockHttpServletResponse();
        performRequest(filter, request, first);
        assertThat(first.getStatus()).isEqualTo(200);

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/search");
        second.setContextPath(null);
        second.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        performRequest(filter, second, secondResponse);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void requestUri_notActuallyPrefixedByItsOwnContextPath_fallsBackToTheRawUri() throws Exception {
        // Defensive edge case: contextPath is non-empty but the request URI
        // doesn't start with it, so the strip is skipped and the raw URI is matched instead.
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(1), List.of("/api/search"), List.of("/api/partner/**"), JWT_SERVICE, 60);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search");
        request.setContextPath("/other-app");
        request.setRemoteAddr("10.0.0.8");
        MockHttpServletResponse first = new MockHttpServletResponse();
        performRequest(filter, request, first);
        assertThat(first.getStatus()).isEqualTo(200);

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/search");
        second.setContextPath("/other-app");
        second.setRemoteAddr("10.0.0.8");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        performRequest(filter, second, secondResponse);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void nonMatchingPath_isNeverRateLimitedRegardlessOfVolume() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(1), List.of("/api/search"), List.of("/api/partner/**"), JWT_SERVICE, 60);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
            request.setRemoteAddr("10.0.0.5");
            MockHttpServletResponse response = new MockHttpServletResponse();

            performRequest(filter, request, response);

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void partnerKeyedPath_withValidPartnerApiToken_isRateLimitedByClientIdNotIp() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(1), List.of(), List.of("/api/partner/**"), JWT_SERVICE, 60);
        String tokenA = JWT_SERVICE.generateAccessToken("pk_clientA", List.of("PARTNER_API"));
        String tokenB = JWT_SERVICE.generateAccessToken("pk_clientB", List.of("PARTNER_API"));

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/partner/routes");
        first.addHeader("Authorization", "Bearer " + tokenA);
        first.setRemoteAddr("10.0.0.9"); // same IP as the second request...
        performRequest(filter, first, new MockHttpServletResponse());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/partner/routes");
        second.addHeader("Authorization", "Bearer " + tokenB); // ...but a different partner token
        second.setRemoteAddr("10.0.0.9");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        performRequest(filter, second, secondResponse);

        assertThat(secondResponse.getStatus()).isEqualTo(200); // clientB has its own bucket, unaffected by clientA
    }

    @Test
    void partnerKeyedPath_sameClientIdTwice_sharesOneBucket() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(1), List.of(), List.of("/api/partner/**"), JWT_SERVICE, 60);
        String token = JWT_SERVICE.generateAccessToken("pk_clientA", List.of("PARTNER_API"));

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/partner/routes");
        first.addHeader("Authorization", "Bearer " + token);
        first.setRemoteAddr("10.0.0.10");
        performRequest(filter, first, new MockHttpServletResponse());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/partner/routes");
        second.addHeader("Authorization", "Bearer " + token);
        second.setRemoteAddr("10.0.0.11"); // different IP, same clientId
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        performRequest(filter, second, secondResponse);

        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void partnerKeyedPath_withoutAValidToken_fallsBackToIpKeying() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(1), List.of(), List.of("/api/partner/**"), JWT_SERVICE, 60);

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/partner/routes");
        first.setRemoteAddr("10.0.0.12");
        performRequest(filter, first, new MockHttpServletResponse());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/partner/routes");
        second.setRemoteAddr("10.0.0.12");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        performRequest(filter, second, secondResponse);

        assertThat(secondResponse.getStatus()).isEqualTo(429); // same IP, no token to key by instead
    }

    @Test
    void partnerKeyedPath_withNonPartnerApiToken_fallsBackToIpKeying() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(newLimiter(1), List.of(), List.of("/api/partner/**"), JWT_SERVICE, 60);
        String customerToken = JWT_SERVICE.generateAccessToken("alice", List.of("CUSTOMER"));

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/partner/routes");
        first.addHeader("Authorization", "Bearer " + customerToken);
        first.setRemoteAddr("10.0.0.13");
        performRequest(filter, first, new MockHttpServletResponse());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/partner/routes");
        second.addHeader("Authorization", "Bearer " + customerToken);
        second.setRemoteAddr("10.0.0.13");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        performRequest(filter, second, secondResponse);

        assertThat(secondResponse.getStatus()).isEqualTo(429); // CUSTOMER role isn't PARTNER_API, so IP keying applies
    }
}
