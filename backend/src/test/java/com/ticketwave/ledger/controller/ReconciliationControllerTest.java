package com.ticketwave.ledger.controller;

import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.ledger.dto.ReconciliationReportResponse;
import com.ticketwave.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/ledger/reconciliation requires authentication. The
 * ADMIN-only restriction itself lives on LedgerService.reconcile() via
 * @PreAuthorize and is only actually enforced through Spring's
 * method-security proxy around the real bean — since this test mocks
 * LedgerService out entirely, it can't exercise that proxy (same reasoning
 * as AuditControllerTest).
 */
@WebMvcTest(ReconciliationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private LedgerService ledgerService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("admin1", List.of("ADMIN"));
    }

    @Test
    void getReconciliationReport_withoutAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(get("/api/ledger/reconciliation")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-02-01T00:00:00Z"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReconciliationReport_withValidToken_returns200() throws Exception {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        given(ledgerService.reconcile(from, to)).willReturn(new ReconciliationReportResponse(
                from, to, new BigDecimal("500.00"), 5L, new BigDecimal("100.00"), 1L,
                BigDecimal.ZERO, 0L, new BigDecimal("400.00")));

        mockMvc.perform(get("/api/ledger/reconciliation")
                        .header("Authorization", bearerToken)
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-02-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPayments").value(500.00))
                .andExpect(jsonPath("$.paymentCount").value(5))
                .andExpect(jsonPath("$.totalRefunds").value(100.00))
                .andExpect(jsonPath("$.netAmount").value(400.00));
    }
}
