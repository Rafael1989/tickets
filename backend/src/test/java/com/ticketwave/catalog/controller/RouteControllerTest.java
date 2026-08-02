package com.ticketwave.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.dto.ScheduleResponse;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.catalog.entity.ScheduleStatus;
import com.ticketwave.catalog.service.RouteService;
import com.ticketwave.catalog.service.ScheduleManagementService;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.pricing.dto.FareRuleResponse;
import com.ticketwave.pricing.service.FareRuleService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private RouteService routeService;
    @MockitoBean
    private ScheduleManagementService scheduleManagementService;
    @MockitoBean
    private FareRuleService fareRuleService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("operator1", List.of("OPERATOR"));
    }

    @Test
    void createRoute_withoutAuthorizationHeader_isRejected() throws Exception {
        RouteRequest request = new RouteRequest(RouteType.BUS, "NYC", "Boston", null, 240);

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRoute_withValidToken_usesAuthenticatedUsernameAndReturns201() throws Exception {
        RouteRequest request = new RouteRequest(RouteType.BUS, "NYC", "Boston", null, 240);
        RouteResponse response = new RouteResponse(1L, 1L, RouteType.BUS, "NYC", "Boston", null, 240);
        given(routeService.createRoute(eq("operator1"), any())).willReturn(response);

        mockMvc.perform(post("/api/routes")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("NYC"));
    }

    @Test
    void updateRoute_withValidToken_returns200() throws Exception {
        RouteRequest request = new RouteRequest(RouteType.TRAIN, "Boston", "NYC", null, 200);
        RouteResponse response = new RouteResponse(1L, 1L, RouteType.TRAIN, "Boston", "NYC", null, 200);
        given(routeService.updateRoute(eq("operator1"), eq(1L), any())).willReturn(response);

        mockMvc.perform(put("/api/routes/1")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRAIN"));
    }

    @Test
    void listSchedulesForRoute_withValidToken_returns200() throws Exception {
        given(scheduleManagementService.listSchedulesForRoute("operator1", 1L)).willReturn(List.of(
                new ScheduleResponse(10L, 1L, java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600),
                        new java.math.BigDecimal("20.00"), "USD", ScheduleStatus.SCHEDULED, null, null)));

        mockMvc.perform(get("/api/routes/1/schedules").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void listFareRulesForRoute_withValidToken_returns200() throws Exception {
        given(fareRuleService.listFareRulesForRoute("operator1", 1L)).willReturn(List.of(
                new FareRuleResponse(1L, 1L, "economy", java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600),
                        new java.math.BigDecimal("0.2000"))));

        mockMvc.perform(get("/api/routes/1/fare-rules").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seatClass").value("economy"));
    }

    @Test
    void listMyRoutes_withValidToken_returns200() throws Exception {
        given(routeService.listMyRoutes("operator1")).willReturn(
                List.of(new RouteResponse(1L, 1L, RouteType.BUS, "NYC", "Boston", null, 240)));

        mockMvc.perform(get("/api/routes/mine").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].destination").value("Boston"));
    }
}
