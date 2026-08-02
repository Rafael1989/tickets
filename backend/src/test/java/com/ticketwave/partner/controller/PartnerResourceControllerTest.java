package com.ticketwave.partner.controller;

import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.partner.service.PartnerResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartnerResourceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class PartnerResourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PartnerResourceService partnerResourceService;

    private String partnerApiBearerToken;

    @BeforeEach
    void issueToken() {
        partnerApiBearerToken = "Bearer " + jwtService.generateAccessToken("pk_abc", List.of("PARTNER_API"));
    }

    @Test
    void listRoutes_withoutAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(get("/api/partner/routes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRoutes_withPartnerApiToken_returns200() throws Exception {
        given(partnerResourceService.listRoutes("pk_abc")).willReturn(List.of(
                new RouteResponse(1L, 2L, RouteType.BUS, "NYC", "Boston", null, 240)));

        mockMvc.perform(get("/api/partner/routes").header("Authorization", partnerApiBearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origin").value("NYC"));
    }
}
