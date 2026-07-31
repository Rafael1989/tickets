package com.ticketwave.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.entity.RouteType;
import com.ticketwave.catalog.service.RouteService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void listMyRoutes_withValidToken_returns200() throws Exception {
        given(routeService.listMyRoutes("operator1")).willReturn(
                List.of(new RouteResponse(1L, 1L, RouteType.BUS, "NYC", "Boston", null, 240)));

        mockMvc.perform(get("/api/routes/mine").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].destination").value("Boston"));
    }
}
