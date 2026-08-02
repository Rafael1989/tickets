package com.ticketwave.contract;

import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.controller.AuthController;
import com.ticketwave.auth.dto.LoginResponse;
import com.ticketwave.auth.exception.InvalidCredentialsException;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.auth.service.AuthService;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

/**
 * Every generated test under contracts/auth/ (see
 * spring-cloud-contract-maven-plugin's baseClassMappings in pom.xml) extends
 * this class. AuthService is mocked exactly like AuthControllerTest — this
 * verifies the *shape* of the request/response the API actually honors
 * (status codes, field names/types), not AuthService's business rules
 * (covered separately by AuthServiceImplTest). Success vs. failure is
 * selected by the request's password, since each contract only controls the
 * HTTP request/response, not this class's stubbing.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
public abstract class AuthContractBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @BeforeEach
    void setUpContractBase() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        given(authService.login(argThat(request -> request != null && "password123".equals(request.password()))))
                .willReturn(new LoginResponse("jwt-token", "Bearer", 900L));
        given(authService.login(argThat(request -> request != null && !"password123".equals(request.password()))))
                .willThrow(new InvalidCredentialsException());
    }
}
