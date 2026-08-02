package com.ticketwave.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.catalog.dto.VehicleRequest;
import com.ticketwave.catalog.dto.VehicleResponse;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.catalog.service.VehicleService;
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

@WebMvcTest(VehicleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private VehicleService vehicleService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("operator1", List.of("OPERATOR"));
    }

    @Test
    void createVehicle_withoutAuthorizationHeader_isRejected() throws Exception {
        VehicleRequest request = new VehicleRequest(RouteType.BUS, "BUS-1234", 45, null);

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createVehicle_withValidToken_returns201() throws Exception {
        VehicleRequest request = new VehicleRequest(RouteType.BUS, "BUS-1234", 45, "Volvo 9800");
        VehicleResponse response = new VehicleResponse(5L, 1L, RouteType.BUS, "BUS-1234", 45, "Volvo 9800");
        given(vehicleService.createVehicle(eq("operator1"), any())).willReturn(response);

        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identifier").value("BUS-1234"));
    }

    @Test
    void listMyVehicles_withValidToken_returns200() throws Exception {
        given(vehicleService.listMyVehicles("operator1")).willReturn(
                List.of(new VehicleResponse(5L, 1L, RouteType.BUS, "BUS-1234", 45, null)));

        mockMvc.perform(get("/api/vehicles/mine").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].identifier").value("BUS-1234"));
    }
}
