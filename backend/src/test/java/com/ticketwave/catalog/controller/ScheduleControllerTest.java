package com.ticketwave.catalog.controller;

import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.catalog.dto.ScheduleSearchResult;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.exception.SeatUnavailableException;
import com.ticketwave.catalog.service.ScheduleSearchService;
import com.ticketwave.catalog.service.SeatHoldService;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms GET /api/schedules/{id} and /{id}/seats are public (guest
 * browsing), that a missing schedule maps to 404 via GlobalExceptionHandler,
 * and that the hold/release endpoints require authentication.
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

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ScheduleSearchService scheduleSearchService;

    @MockitoBean
    private SeatHoldService seatHoldService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("alice", List.of("CUSTOMER"));
    }

    private static SeatResponse seatResponse(long id, SeatStatus status) {
        return new SeatResponse(id, 1L, "1A", "economy", status, BigDecimal.ONE, new BigDecimal("25.00"), null, false);
    }

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
    void getSeats_withoutAuthorizationHeader_isPublicAndPassesNullUsername() throws Exception {
        given(scheduleSearchService.getSeatsForSchedule(eq(1L), isNull())).willReturn(List.of(
                seatResponse(1L, SeatStatus.AVAILABLE),
                seatResponse(2L, SeatStatus.BOOKED)));

        mockMvc.perform(get("/api/schedules/1/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].status").value("BOOKED"));
    }

    @Test
    void getSeats_withAuthenticatedCaller_passesUsernameToService() throws Exception {
        given(scheduleSearchService.getSeatsForSchedule(eq(1L), eq("alice"))).willReturn(List.of());

        mockMvc.perform(get("/api/schedules/1/seats").header("Authorization", bearerToken))
                .andExpect(status().isOk());

        verify(scheduleSearchService).getSeatsForSchedule(1L, "alice");
    }

    @Test
    void holdSeat_withoutAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(post("/api/schedules/1/seats/5/hold"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void holdSeat_withValidToken_usesAuthenticatedUsernameAndReturnsTheEnrichedSeat() throws Exception {
        // The hold response is re-fetched through ScheduleSearchService
        // (not mapped directly off the entity SeatHoldService returns) so it
        // carries the same real estimatedFare/heldByMe the seat map shows.
        given(scheduleSearchService.getSeatsForSchedule(eq(1L), eq("alice"))).willReturn(List.of(
                seatResponse(5L, SeatStatus.HELD), seatResponse(6L, SeatStatus.AVAILABLE)));

        mockMvc.perform(post("/api/schedules/1/seats/5/hold").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.estimatedFare").value(25.00));

        verify(seatHoldService).holdSeatForUsername(5L, "alice");
    }

    @Test
    void holdSeat_whenSeatVanishesBeforeReFetch_returns404() throws Exception {
        given(scheduleSearchService.getSeatsForSchedule(eq(1L), eq("alice"))).willReturn(List.of());

        mockMvc.perform(post("/api/schedules/1/seats/5/hold").header("Authorization", bearerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void holdSeat_whenSeatUnavailable_returns409() throws Exception {
        given(seatHoldService.holdSeatForUsername(5L, "alice")).willThrow(new SeatUnavailableException(5L));

        mockMvc.perform(post("/api/schedules/1/seats/5/hold").header("Authorization", bearerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void releaseSeat_withoutAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(delete("/api/schedules/1/seats/5/hold"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void releaseSeat_withValidToken_usesAuthenticatedUsernameAndReturns204() throws Exception {
        mockMvc.perform(delete("/api/schedules/1/seats/5/hold").header("Authorization", bearerToken))
                .andExpect(status().isNoContent());

        verify(seatHoldService).releaseOwnHold(5L, "alice");
    }
}
