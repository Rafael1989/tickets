package com.ticketwave.catalog.controller;

import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.catalog.dto.ScheduleSearchResult;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
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
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms GET /api/schedules/{id} and /{id}/seats are public (guest
 * browsing) and that a missing schedule maps to 404 via GlobalExceptionHandler.
 */
@WebMvcTest(ScheduleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleSearchService scheduleSearchService;

    @Test
    void getSchedule_withoutAuthorizationHeader_isPublicAndReturnsDetails() throws Exception {
        ScheduleSearchResult result = new ScheduleSearchResult(
                1L, 10L, RouteType.BUS, "NYC", "Boston", null,
                Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"),
                new BigDecimal("25.00"), "USD", ScheduleStatus.SCHEDULED, 5L);
        given(scheduleSearchService.getScheduleDetails(1L)).willReturn(result);

        mockMvc.perform(get("/api/schedules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(1))
                .andExpect(jsonPath("$.origin").value("NYC"));
    }

    @Test
    void getSchedule_whenMissing_returns404() throws Exception {
        given(scheduleSearchService.getScheduleDetails(99L)).willThrow(new ScheduleNotFoundException(99L));

        mockMvc.perform(get("/api/schedules/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SCHEDULE_NOT_FOUND"));
    }

    @Test
    void getSeats_withoutAuthorizationHeader_isPublicAndReturnsAllSeats() throws Exception {
        given(scheduleSearchService.getSeatsForSchedule(1L)).willReturn(List.of(
                new SeatResponse(1L, 1L, "1A", "economy", SeatStatus.AVAILABLE, BigDecimal.ONE),
                new SeatResponse(2L, 1L, "1B", "economy", SeatStatus.BOOKED, BigDecimal.ONE)));

        mockMvc.perform(get("/api/schedules/1/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].status").value("BOOKED"));
    }
}
