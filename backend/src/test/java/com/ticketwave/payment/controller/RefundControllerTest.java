package com.ticketwave.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.payment.dto.RefundDecision;
import com.ticketwave.payment.dto.RefundDecisionRequest;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.entity.RefundStatus;
import com.ticketwave.payment.service.RefundService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RefundController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class RefundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private RefundService refundService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("support1", List.of("SUPPORT"));
    }

    @Test
    void processRefund_withoutAuthorizationHeader_isRejected() throws Exception {
        RefundDecisionRequest request = new RefundDecisionRequest(RefundDecision.APPROVE);

        mockMvc.perform(put("/api/refunds/1/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void processRefund_withValidToken_usesAuthenticatedUsernameAndReturns200() throws Exception {
        RefundDecisionRequest request = new RefundDecisionRequest(RefundDecision.APPROVE);
        given(refundService.processRefund(1L, "support1", RefundDecision.APPROVE)).willReturn(
                new RefundResponse(1L, 1L, new BigDecimal("50.00"), "FULL_REFUND", RefundStatus.PROCESSED, 9L, Instant.now()));

        mockMvc.perform(put("/api/refunds/1/process")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }
}
