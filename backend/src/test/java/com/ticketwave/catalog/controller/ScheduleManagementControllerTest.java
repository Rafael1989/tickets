package com.ticketwave.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.catalog.dto.ScheduleRequest;
import com.ticketwave.catalog.dto.ScheduleResponse;
import com.ticketwave.catalog.dto.SeatRequest;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.dto.SeatUpdateRequest;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.service.ScheduleManagementService;
import com.ticketwave.catalog.service.SeatManagementService;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleManagementController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class ScheduleManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private ScheduleManagementService scheduleManagementService;

    @MockitoBean
    private SeatManagementService seatManagementService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("operator1", List.of("OPERATOR"));
    }

    @Test
    void createSchedule_withoutAuthorizationHeader_isRejected() throws Exception {
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null);

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createSchedule_withValidToken_returns201() throws Exception {
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("20.00"), "USD", null);
        given(scheduleManagementService.createSchedule(eq("operator1"), any())).willReturn(
                new ScheduleResponse(10L, 1L, request.departureTime(), request.arrivalTime(),
                        new BigDecimal("20.00"), "USD", ScheduleStatus.SCHEDULED, null, null));

        mockMvc.perform(post("/api/schedules")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routeId").value(1));
    }

    @Test
    void updateSchedule_withValidToken_returns200() throws Exception {
        ScheduleRequest request = new ScheduleRequest(1L, Instant.now(), Instant.now().plusSeconds(7200),
                new BigDecimal("35.00"), "EUR", ScheduleStatus.DELAYED);
        given(scheduleManagementService.updateSchedule(eq("operator1"), eq(10L), any())).willReturn(
                new ScheduleResponse(10L, 1L, request.departureTime(), request.arrivalTime(),
                        new BigDecimal("35.00"), "EUR", ScheduleStatus.DELAYED, null, null));

        mockMvc.perform(put("/api/schedules/10")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELAYED"));
    }

    @Test
    void addSeat_withValidToken_returns201() throws Exception {
        SeatRequest request = new SeatRequest(1L, "1A", "economy", null, new BigDecimal("1.000"));
        given(seatManagementService.addSeat(eq("operator1"), any())).willReturn(
                new SeatResponse(5L, 1L, "1A", "economy", SeatStatus.AVAILABLE, new BigDecimal("1.000"), null, null, false));

        mockMvc.perform(post("/api/seats")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatNumber").value("1A"));
    }

    @Test
    void updateSeat_withValidToken_returns200() throws Exception {
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.HELD, new BigDecimal("1.500"));
        given(seatManagementService.updateSeat(eq("operator1"), eq(5L), any())).willReturn(
                new SeatResponse(5L, 1L, "1A", "economy", SeatStatus.HELD, new BigDecimal("1.500"), null, null, false));

        mockMvc.perform(put("/api/seats/5")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"));
    }
}
