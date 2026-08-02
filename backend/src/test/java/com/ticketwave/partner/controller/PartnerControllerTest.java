package com.ticketwave.partner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.partner.dto.PartnerCredentialIssuedResponse;
import com.ticketwave.partner.dto.PartnerRequest;
import com.ticketwave.partner.dto.PartnerResponse;
import com.ticketwave.partner.dto.PartnerStatusUpdateRequest;
import com.ticketwave.partner.dto.PartnerWebhookIssuedResponse;
import com.ticketwave.partner.dto.PartnerWebhookRequest;
import com.ticketwave.partner.dto.WebhookStatusUpdateRequest;
import com.ticketwave.partner.entity.PartnerStatus;
import com.ticketwave.partner.entity.WebhookStatus;
import com.ticketwave.partner.service.PartnerApiCredentialService;
import com.ticketwave.partner.service.PartnerService;
import com.ticketwave.partner.service.PartnerWebhookService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/partners/** requires authentication. The ADMIN-only
 * restriction itself lives on the service layer via @PreAuthorize — see
 * AuditControllerTest's class comment for why a mocked-service WebMvcTest
 * can't exercise that.
 */
@WebMvcTest(PartnerController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class PartnerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PartnerService partnerService;
    @MockitoBean
    private PartnerApiCredentialService credentialService;
    @MockitoBean
    private PartnerWebhookService webhookService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("admin1", List.of("ADMIN"));
    }

    @Test
    void createPartner_withoutAuthorizationHeader_isRejected() throws Exception {
        PartnerRequest request = new PartnerRequest("Acme Transit", "ops@acme.example", new BigDecimal("0.1000"));

        mockMvc.perform(post("/api/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPartner_withValidToken_returns201() throws Exception {
        PartnerRequest request = new PartnerRequest("Acme Transit", "ops@acme.example", new BigDecimal("0.1000"));
        given(partnerService.createPartner(eq("admin1"), org.mockito.ArgumentMatchers.any())).willReturn(
                new PartnerResponse(9L, "Acme Transit", "ops@acme.example", PartnerStatus.PENDING, new BigDecimal("0.1000"), Instant.now()));

        mockMvc.perform(post("/api/partners")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void listPartners_withValidToken_returns200() throws Exception {
        given(partnerService.listPartners()).willReturn(List.of(
                new PartnerResponse(9L, "Acme Transit", "ops@acme.example", PartnerStatus.ACTIVE, BigDecimal.TEN, Instant.now())));

        mockMvc.perform(get("/api/partners").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Acme Transit"));
    }

    @Test
    void getPartner_withValidToken_returns200() throws Exception {
        given(partnerService.getPartner(9L)).willReturn(
                new PartnerResponse(9L, "Acme Transit", "ops@acme.example", PartnerStatus.ACTIVE, BigDecimal.TEN, Instant.now()));

        mockMvc.perform(get("/api/partners/9").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void updateStatus_withValidToken_returns200() throws Exception {
        PartnerStatusUpdateRequest request = new PartnerStatusUpdateRequest(PartnerStatus.ACTIVE);
        given(partnerService.updateStatus("admin1", 9L, PartnerStatus.ACTIVE)).willReturn(
                new PartnerResponse(9L, "Acme Transit", "ops@acme.example", PartnerStatus.ACTIVE, BigDecimal.TEN, Instant.now()));

        mockMvc.perform(put("/api/partners/9/status")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void issueCredential_withValidToken_returns201WithSecret() throws Exception {
        given(credentialService.issueCredential("admin1", 9L)).willReturn(
                new PartnerCredentialIssuedResponse(1L, 9L, "pk_abc", "raw-secret", Instant.now()));

        mockMvc.perform(post("/api/partners/9/credentials").header("Authorization", bearerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientSecret").value("raw-secret"));
    }

    @Test
    void revokeCredential_withValidToken_returns204() throws Exception {
        mockMvc.perform(put("/api/partners/credentials/1/revoke").header("Authorization", bearerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerWebhook_withValidToken_returns201WithSecret() throws Exception {
        PartnerWebhookRequest request = new PartnerWebhookRequest("https://partner.example/hook", "BOOKING_CANCELLED");
        given(webhookService.registerWebhook(eq("admin1"), eq(9L), org.mockito.ArgumentMatchers.any())).willReturn(
                new PartnerWebhookIssuedResponse(1L, 9L, "https://partner.example/hook", "raw-secret", "BOOKING_CANCELLED", Instant.now()));

        mockMvc.perform(post("/api/partners/9/webhooks")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").value("raw-secret"));
    }

    @Test
    void updateWebhookStatus_withValidToken_returns200() throws Exception {
        WebhookStatusUpdateRequest request = new WebhookStatusUpdateRequest(WebhookStatus.DISABLED);
        given(webhookService.updateStatus("admin1", 1L, WebhookStatus.DISABLED)).willReturn(
                new com.ticketwave.partner.dto.PartnerWebhookResponse(1L, 9L, "https://partner.example/hook", "BOOKING_CANCELLED", WebhookStatus.DISABLED, Instant.now()));

        mockMvc.perform(put("/api/partners/webhooks/1/status")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }
}
