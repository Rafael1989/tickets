package com.ticketwave.pricing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.pricing.dto.PromoCodeApplication;
import com.ticketwave.pricing.dto.PromoCodeRequest;
import com.ticketwave.pricing.dto.PromoCodeResponse;
import com.ticketwave.pricing.dto.PromoCodeStatusUpdateRequest;
import com.ticketwave.pricing.dto.PromoValidationRequest;
import com.ticketwave.pricing.entity.DiscountType;
import com.ticketwave.pricing.entity.PromoCode;
import com.ticketwave.pricing.exception.PromoCodeNotApplicableException;
import com.ticketwave.pricing.exception.PromoCodeNotFoundException;
import com.ticketwave.pricing.service.PricingService;
import com.ticketwave.pricing.service.PromoCodeService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms POST /api/promos/validate is public (no bearer token needed) and
 * never touches redemption state — that's PricingServiceImplTest's job.
 */
@WebMvcTest(PromoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class PromoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private PricingService pricingService;
    @MockitoBean
    private PromoCodeService promoCodeService;

    @Autowired
    private JwtService jwtService;

    private String adminBearerToken;

    @BeforeEach
    void issueToken() {
        adminBearerToken = "Bearer " + jwtService.generateAccessToken("admin1", List.of("ADMIN"));
    }

    private static PromoCode promoCode(String code) {
        return PromoCode.builder().id(1L).code(code).discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00")).build();
    }

    private static PromoCodeResponse promoCodeResponse() {
        return new PromoCodeResponse(1L, "SAVE20", DiscountType.PERCENTAGE, new BigDecimal("20.00"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), null, 0, true, Instant.now());
    }

    @Test
    void validate_withoutAuthorizationHeader_stillSucceeds() throws Exception {
        given(pricingService.previewPromoCode("SAVE20", new BigDecimal("100.00")))
                .willReturn(new PromoCodeApplication(promoCode("SAVE20"), new BigDecimal("20.00")));

        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PromoValidationRequest("SAVE20", new BigDecimal("100.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SAVE20"))
                .andExpect(jsonPath("$.discountAmount").value(20.00))
                .andExpect(jsonPath("$.totalAfterDiscount").value(80.00));
    }

    @Test
    void validate_whenCodeUnknown_returns404() throws Exception {
        given(pricingService.previewPromoCode("NOPE", BigDecimal.TEN))
                .willThrow(new PromoCodeNotFoundException("NOPE"));

        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PromoValidationRequest("NOPE", BigDecimal.TEN))))
                .andExpect(status().isNotFound());
    }

    @Test
    void validate_whenCodeNotApplicable_returns409() throws Exception {
        given(pricingService.previewPromoCode(any(), any()))
                .willThrow(new PromoCodeNotApplicableException("EXPIRED", "outside its validity window"));

        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PromoValidationRequest("EXPIRED", BigDecimal.TEN))))
                .andExpect(status().isConflict());
    }

    @Test
    void validate_withBlankCode_returns400() throws Exception {
        String invalidPayload = """
                {"code":"","subtotal":10.00}
                """;

        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void validate_withNegativeSubtotal_returns400() throws Exception {
        String invalidPayload = """
                {"code":"SAVE20","subtotal":-5.00}
                """;

        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPromoCode_withoutAuthorizationHeader_isRejected() throws Exception {
        PromoCodeRequest request = new PromoCodeRequest("SAVE20", DiscountType.PERCENTAGE, new BigDecimal("20.00"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2030-12-31T00:00:00Z"), null);

        mockMvc.perform(post("/api/promos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPromoCode_withValidToken_returns201() throws Exception {
        PromoCodeRequest request = new PromoCodeRequest("SAVE20", DiscountType.PERCENTAGE, new BigDecimal("20.00"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2030-12-31T00:00:00Z"), null);
        given(promoCodeService.createPromoCode(eq("admin1"), any())).willReturn(promoCodeResponse());

        mockMvc.perform(post("/api/promos")
                        .header("Authorization", adminBearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SAVE20"));
    }

    @Test
    void listPromoCodes_withValidToken_returns200() throws Exception {
        given(promoCodeService.listPromoCodes()).willReturn(List.of(promoCodeResponse()));

        mockMvc.perform(get("/api/promos").header("Authorization", adminBearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SAVE20"));
    }

    @Test
    void updateStatus_withValidToken_returns200() throws Exception {
        PromoCodeStatusUpdateRequest request = new PromoCodeStatusUpdateRequest(false);
        given(promoCodeService.updateStatus("admin1", 1L, false)).willReturn(
                new PromoCodeResponse(1L, "SAVE20", DiscountType.PERCENTAGE, new BigDecimal("20.00"),
                        Instant.now(), Instant.now(), null, 0, false, Instant.now()));

        mockMvc.perform(put("/api/promos/1/status")
                        .header("Authorization", adminBearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
