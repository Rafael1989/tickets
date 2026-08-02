package com.ticketwave.reporting.controller;

import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.reporting.dto.OperatorReportResponse;
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
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperatorReportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class OperatorReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private com.ticketwave.reporting.service.OperatorReportService operatorReportService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("operator1", List.of("OPERATOR"));
    }

    @Test
    void getReport_withoutAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(get("/api/operator/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReport_withValidToken_returns200() throws Exception {
        given(operatorReportService.getReport("operator1")).willReturn(
                new OperatorReportResponse(List.of(), 0, BigDecimal.ZERO));

        mockMvc.perform(get("/api/operator/reports").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConfirmedBookings").value(0));
    }
}
