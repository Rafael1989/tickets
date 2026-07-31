package com.ticketwave.audit.controller;

import com.ticketwave.audit.dto.AuditLogResponse;
import com.ticketwave.audit.service.AuditService;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/audit requires authentication. The ADMIN-only restriction
 * itself lives on AuditService.listAll() via @PreAuthorize and is only
 * actually enforced through Spring's method-security proxy around the real
 * bean — since this test mocks AuditService out entirely, it can't exercise
 * that proxy (same reasoning as BookingControllerTest/PassengerControllerTest).
 */
@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuditService auditService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("admin1", List.of("ADMIN"));
    }

    @Test
    void listAudit_withoutAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAudit_withValidToken_returns200() throws Exception {
        given(auditService.listAll()).willReturn(List.of(
                new AuditLogResponse(1L, "alice", "USER_REGISTERED", "USER", 1L, "role=CUSTOMER", Instant.now())));

        mockMvc.perform(get("/api/audit").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("USER_REGISTERED"));
    }
}
