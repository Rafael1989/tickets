package com.ticketwave.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.user.dto.PassengerRequest;
import com.ticketwave.user.dto.PassengerResponse;
import com.ticketwave.user.service.PassengerService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/passengers requires authentication and that the
 * authenticated username (not any client-supplied id) is what reaches the
 * service layer.
 */
@WebMvcTest(PassengerController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class PassengerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private PassengerService passengerService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("alice", List.of("CUSTOMER"));
    }

    @Test
    void createPassenger_withoutAuthorizationHeader_isRejected() throws Exception {
        PassengerRequest request = new PassengerRequest("Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");

        mockMvc.perform(post("/api/passengers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPassenger_withValidToken_usesAuthenticatedUsernameAndReturns201() throws Exception {
        PassengerRequest request = new PassengerRequest("Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");
        PassengerResponse response = new PassengerResponse(100L, 1L, "Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");
        given(passengerService.createPassenger(eq("alice"), any())).willReturn(response);

        mockMvc.perform(post("/api/passengers")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"));
    }

    @Test
    void listMyPassengers_withValidToken_returns200() throws Exception {
        PassengerResponse response = new PassengerResponse(100L, 1L, "Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");
        given(passengerService.listMyPassengers("alice")).willReturn(List.of(response));

        mockMvc.perform(get("/api/passengers/me").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Jane Doe"));
    }
}
