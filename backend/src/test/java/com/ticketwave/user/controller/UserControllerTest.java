package com.ticketwave.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.user.dto.RoleUpdateRequest;
import com.ticketwave.user.dto.UserRequest;
import com.ticketwave.user.dto.UserResponse;
import com.ticketwave.user.entity.UserRole;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.service.UserService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/users** requires authentication. The ADMIN-only restriction
 * itself lives on UserService via @PreAuthorize — see AuditControllerTest's
 * class comment for why a mocked-service WebMvcTest can't exercise that.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("admin1", List.of("ADMIN"));
    }

    @Test
    void listUsers_withoutAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_withValidToken_returns200() throws Exception {
        given(userService.listUsers()).willReturn(List.of(
                new UserResponse(1L, "alice", "alice@example.com", UserRole.CUSTOMER, Instant.now())));

        mockMvc.perform(get("/api/users").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    void getUser_withValidToken_returns200() throws Exception {
        given(userService.getUser(1L)).willReturn(
                new UserResponse(1L, "alice", "alice@example.com", UserRole.CUSTOMER, Instant.now()));

        mockMvc.perform(get("/api/users/1").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void getUser_whenNotFound_returns404() throws Exception {
        given(userService.getUser(99L)).willThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/api/users/99").header("Authorization", bearerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void createUser_withValidToken_usesAuthenticatedUsernameAndReturns201() throws Exception {
        UserRequest request = new UserRequest("operator2", "password123", "operator2@example.com", UserRole.OPERATOR);
        given(userService.createUser(eq("admin1"), any())).willReturn(
                new UserResponse(3L, "operator2", "operator2@example.com", UserRole.OPERATOR, Instant.now()));

        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    void updateRole_withValidToken_returns200() throws Exception {
        RoleUpdateRequest request = new RoleUpdateRequest(UserRole.SUPPORT);
        given(userService.updateRole("admin1", 1L, UserRole.SUPPORT)).willReturn(
                new UserResponse(1L, "alice", "alice@example.com", UserRole.SUPPORT, Instant.now()));

        mockMvc.perform(put("/api/users/1/role")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPPORT"));
    }
}
