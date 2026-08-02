package com.ticketwave.partner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.partner.dto.PartnerTokenRequest;
import com.ticketwave.partner.dto.PartnerTokenResponse;
import com.ticketwave.partner.exception.InvalidPartnerCredentialsException;
import com.ticketwave.partner.service.PartnerApiCredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/oauth/token is public — no bearer token required to reach it. */
@WebMvcTest(PartnerTokenController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class PartnerTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PartnerApiCredentialService credentialService;

    @Test
    void issueToken_withValidCredentials_returns200WithoutAnyBearerToken() throws Exception {
        PartnerTokenRequest request = new PartnerTokenRequest("pk_abc", "secret");
        given(credentialService.issueToken("pk_abc", "secret")).willReturn(new PartnerTokenResponse("jwt", "Bearer", 900L));

        mockMvc.perform(post("/api/oauth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void issueToken_withInvalidCredentials_returns401() throws Exception {
        PartnerTokenRequest request = new PartnerTokenRequest("pk_abc", "wrong");
        given(credentialService.issueToken("pk_abc", "wrong")).willThrow(new InvalidPartnerCredentialsException());

        mockMvc.perform(post("/api/oauth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issueToken_withBlankClientId_returns400() throws Exception {
        mockMvc.perform(post("/api/oauth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"\",\"clientSecret\":\"secret\"}"))
                .andExpect(status().isBadRequest());
    }
}
