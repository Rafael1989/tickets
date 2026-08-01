package com.ticketwave.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.user.dto.PassengerRequest;
import com.ticketwave.user.dto.PassengerResponse;
import com.ticketwave.user.exception.PassengerNotFoundException;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void updatePassenger_withoutAuthorizationHeader_isRejected() throws Exception {
        PassengerRequest request = new PassengerRequest("Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");

        mockMvc.perform(put("/api/passengers/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePassenger_withValidToken_usesAuthenticatedUsernameAndReturns200() throws Exception {
        PassengerRequest request = new PassengerRequest("Jane Updated", LocalDate.of(1990, 1, 1), "passport", "X123456");
        PassengerResponse response = new PassengerResponse(100L, 1L, "Jane Updated", LocalDate.of(1990, 1, 1), "passport", "X123456");
        given(passengerService.updatePassenger(eq("alice"), eq(100L), any())).willReturn(response);

        mockMvc.perform(put("/api/passengers/100")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Updated"));
    }

    @Test
    void updatePassenger_whenNotOwnedByCaller_returns404() throws Exception {
        PassengerRequest request = new PassengerRequest("Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");
        given(passengerService.updatePassenger(eq("alice"), eq(100L), any()))
                .willThrow(new PassengerNotFoundException(100L));

        mockMvc.perform(put("/api/passengers/100")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePassenger_withoutAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(delete("/api/passengers/100"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletePassenger_withValidToken_usesAuthenticatedUsernameAndReturns204() throws Exception {
        doNothing().when(passengerService).deletePassenger("alice", 100L);

        mockMvc.perform(delete("/api/passengers/100").header("Authorization", bearerToken))
                .andExpect(status().isNoContent());

        verify(passengerService).deletePassenger("alice", 100L);
    }

    @Test
    void deletePassenger_whenNotOwnedByCaller_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new PassengerNotFoundException(100L))
                .when(passengerService).deletePassenger("alice", 100L);

        mockMvc.perform(delete("/api/passengers/100").header("Authorization", bearerToken))
                .andExpect(status().isNotFound());
    }
}
