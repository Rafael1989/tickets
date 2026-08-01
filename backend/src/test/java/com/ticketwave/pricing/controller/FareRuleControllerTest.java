package com.ticketwave.pricing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.pricing.dto.FareRuleRequest;
import com.ticketwave.pricing.dto.FareRuleResponse;
import com.ticketwave.pricing.service.FareRuleService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FareRuleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class FareRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private FareRuleService fareRuleService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("operator1", List.of("OPERATOR"));
    }

    @Test
    void createFareRule_withoutAuthorizationHeader_isRejected() throws Exception {
        FareRuleRequest request = new FareRuleRequest(1L, "business", Instant.now(), Instant.now().plusSeconds(3600), new BigDecimal("0.20"));

        mockMvc.perform(post("/api/fare-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createFareRule_withValidToken_returns201() throws Exception {
        FareRuleRequest request = new FareRuleRequest(1L, "business", Instant.now(), Instant.now().plusSeconds(3600), new BigDecimal("0.20"));
        FareRuleResponse response = new FareRuleResponse(9L, 1L, "business", request.validFrom(), request.validTo(), request.surchargeRate());
        given(fareRuleService.createFareRule(eq("operator1"), any())).willReturn(response);

        mockMvc.perform(post("/api/fare-rules")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatClass").value("business"));
    }

    @Test
    void bulkCreateFareRules_withValidToken_returns201() throws Exception {
        FareRuleRequest req1 = new FareRuleRequest(1L, "business", Instant.now(), Instant.now().plusSeconds(3600), new BigDecimal("0.20"));
        FareRuleRequest req2 = new FareRuleRequest(1L, "economy", Instant.now(), Instant.now().plusSeconds(3600), new BigDecimal("0.10"));
        List<FareRuleResponse> responses = List.of(
                new FareRuleResponse(9L, 1L, "business", req1.validFrom(), req1.validTo(), req1.surchargeRate()),
                new FareRuleResponse(10L, 1L, "economy", req2.validFrom(), req2.validTo(), req2.surchargeRate()));
        given(fareRuleService.bulkCreateFareRules(eq("operator1"), any())).willReturn(responses);

        mockMvc.perform(post("/api/fare-rules/bulk")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(req1, req2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
