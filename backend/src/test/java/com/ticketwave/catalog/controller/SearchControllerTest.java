package com.ticketwave.catalog.controller;

import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.catalog.dto.ScheduleSearchCriteria;
import com.ticketwave.catalog.dto.ScheduleSearchResult;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.service.ScheduleSearchService;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms GET /api/search is genuinely public per SecurityConfig (no
 * Authorization header needed) â€” guests must be able to search per US1.
 */
@WebMvcTest(SearchController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleSearchService scheduleSearchService;

    @Test
    void search_withoutAuthorizationHeader_isPublicAndReturnsResults() throws Exception {
        ScheduleSearchResult result = new ScheduleSearchResult(
                1L, 10L, RouteType.BUS, "NYC", "Boston", null,
                Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"),
                new BigDecimal("25.00"), "USD", ScheduleStatus.SCHEDULED, 5L);
        given(scheduleSearchService.search(new ScheduleSearchCriteria(null, "NYC", "Boston", null, null, null, null, null, null)))
                .willReturn(List.of(result));

        mockMvc.perform(get("/api/search").param("origin", "NYC").param("destination", "Boston"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduleId").value(1))
                .andExpect(jsonPath("$[0].availableSeats").value(5));
    }

    @Test
    void search_withDepartureDateAndType_bindsQueryParamsCorrectly() throws Exception {
        given(scheduleSearchService.search(
                new ScheduleSearchCriteria(RouteType.EVENT, null, null, "Arena", LocalDate.of(2026, 8, 1), null, null, null, null)))
                .willReturn(List.of());

        mockMvc.perform(get("/api/search")
                        .param("type", "EVENT")
                        .param("venue", "Arena")
                        .param("departureDate", "2026-08-01"))
                .andExpect(status().isOk());
    }
}
