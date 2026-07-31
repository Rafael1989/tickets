package com.ticketwave.catalog.controller;

import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.service.ScheduleSearchService;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.RateLimitConfig;
import com.ticketwave.config.RateLimitProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the FilterRegistrationBean wiring in RateLimitConfig actually takes
 * effect on a real (if minimal) Spring context — a pure unit test of
 * RateLimitingFilter can't verify the bean registration/order itself, only
 * the filter's own logic once invoked. SecurityConfig is imported too, so
 * /api/search is genuinely public here just like in production, rather than
 * falling back to Spring Boot's default security (which would reject these
 * requests before they even reach the rate limiter).
 */
@WebMvcTest(SearchController.class)
@Import({RateLimitConfig.class, RateLimiter.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties({RateLimitProperties.class, JwtProperties.class})
@TestPropertySource(properties = {
        "ticketwave.rate-limit.requests-per-window=2",
        "ticketwave.rate-limit.window-seconds=60",
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class SearchControllerRateLimitingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleSearchService scheduleSearchService;

    @Test
    void search_exceedingConfiguredLimit_returns429WithRetryAfter() throws Exception {
        given(scheduleSearchService.search(any(ScheduleSearchCriteria.class))).willReturn(List.of());

        mockMvc.perform(get("/api/search").with(req -> {
                    req.setRemoteAddr("192.168.1.50");
                    return req;
                }))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/search").with(req -> {
                    req.setRemoteAddr("192.168.1.50");
                    return req;
                }))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/search").with(req -> {
                    req.setRemoteAddr("192.168.1.50");
                    return req;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }
}
