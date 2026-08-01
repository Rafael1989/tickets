package com.ticketwave.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.user.dto.UserPreferencesRequest;
import com.ticketwave.user.dto.UserPreferencesResponse;
import com.ticketwave.user.service.UserPreferencesService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferencesController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class PreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserPreferencesService preferencesService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("alice", List.of("CUSTOMER"));
    }

    @Test
    void getPreferences_withoutAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(get("/api/users/me/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPreferences_withValidToken_usesAuthenticatedUsernameAndReturns200() throws Exception {
        given(preferencesService.getOrCreateDefault("alice")).willReturn(
                new UserPreferencesResponse(1L, "USD", null, true, Instant.now()));

        mockMvc.perform(get("/api/users/me/preferences").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCurrency").value("USD"));
    }

    @Test
    void updatePreferences_withoutAuthorizationHeader_isRejected() throws Exception {
        UserPreferencesRequest request = new UserPreferencesRequest("EUR", "AISLE", false);

        mockMvc.perform(put("/api/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePreferences_withValidToken_usesAuthenticatedUsernameAndReturns200() throws Exception {
        UserPreferencesRequest request = new UserPreferencesRequest("EUR", "AISLE", false);
        given(preferencesService.update(org.mockito.ArgumentMatchers.eq("alice"), org.mockito.ArgumentMatchers.any()))
                .willReturn(new UserPreferencesResponse(1L, "EUR", "AISLE", false, Instant.now()));

        mockMvc.perform(put("/api/users/me/preferences")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCurrency").value("EUR"))
                .andExpect(jsonPath("$.notificationsEnabled").value(false));
    }

    @Test
    void updatePreferences_withInvalidCurrency_returns400() throws Exception {
        String invalidJson = """
                {"preferredCurrency":"usd","seatPreference":"AISLE","notificationsEnabled":true}
                """;

        mockMvc.perform(put("/api/users/me/preferences")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
