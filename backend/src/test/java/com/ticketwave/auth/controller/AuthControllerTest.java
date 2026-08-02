package com.ticketwave.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.dto.LoginRequest;
import com.ticketwave.auth.dto.LoginResponse;
import com.ticketwave.auth.dto.RegisterRequest;
import com.ticketwave.auth.exception.InvalidCredentialsException;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.auth.service.AuthService;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.user.dto.UserResponse;
import com.ticketwave.user.entity.UserRole;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test exercising the real Spring Security filter chain (SecurityConfig
 * + JwtAuthenticationFilter) without booting JPA/Liquibase/a real datasource,
 * so it runs without Docker/Postgres. AuthService itself is mocked; its own
 * business rules are covered by AuthServiceImplTest.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Jackson's own autoconfiguration isn't part of the @WebMvcTest slice's
    // auto-configuration whitelist, so this is built directly rather than
    // relying on an ObjectMapper bean from the (non-existent) context one.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @Test
    void register_withValidPayload_returns201WithUser() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "password123", "alice@example.com");
        UserResponse response = new UserResponse(1L, "alice", "alice@example.com", UserRole.CUSTOMER, null, Instant.now());
        given(authService.register(any())).willReturn(response);

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_withBlankUsername_returns400WithValidationError() throws Exception {
        String invalidPayload = """
                {"username":"","password":"password123","email":"alice@example.com"}
                """;

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void login_withValidCredentials_returns200WithToken() throws Exception {
        given(authService.login(any())).willReturn(new LoginResponse("jwt-token", "Bearer", 900L));

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        given(authService.login(any())).willThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void anyOtherEndpoint_withoutBearerToken_isRejectedByDefault() throws Exception {
        mockMvc.perform(get("/api/some-protected-resource"))
                .andExpect(status().isUnauthorized());
    }
}
