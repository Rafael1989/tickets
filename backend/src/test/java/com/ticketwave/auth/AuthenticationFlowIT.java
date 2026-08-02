package com.ticketwave.auth;

import com.ticketwave.AbstractIntegrationTest;
import com.ticketwave.auth.dto.LoginRequest;
import com.ticketwave.auth.dto.RegisterRequest;
import com.ticketwave.common.exception.ErrorResponse;
import com.ticketwave.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one full-HTTP-stack integration test in the suite: unlike the
 * service-layer *IT classes (BookingFlowIT, PaymentFlowIT, ...), this goes
 * over real HTTP against the embedded server (see AbstractIntegrationTest's
 * RANDOM_PORT), exercising the full request path — DispatcherServlet, the
 * real Spring Security filter chain (JwtAuthenticationFilter,
 * AuthorizationFilter), controller @Valid binding, and
 * GlobalExceptionHandler — not just the service method being called
 * in-process.
 *
 * Spring Boot 4 / Spring Framework 7 removed TestRestTemplate; RestTestClient
 * is its replacement, bound here to the real server via bindToServer() (as
 * opposed to bindToController(...), which would stay in-process and skip the
 * servlet container + security filter chain this test exists to exercise).
 *
 * Uses a UUID-suffixed username per test run rather than table cleanup,
 * matching the isolation strategy documented on AbstractIntegrationTest.
 */
class AuthenticationFlowIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private static String uniqueUsername() {
        return "ituser-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void register_thenLogin_thenCallProtectedEndpoint_succeedsEndToEnd() {
        String username = uniqueUsername();
        RegisterRequest registerRequest = new RegisterRequest(username, "SecurePass123!", username + "@example.com");

        UserResponse registered = client.post().uri("/api/register")
                .body(registerRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(registered).isNotNull();
        assertThat(registered.username()).isEqualTo(username);
        assertThat(registered.role().name()).isEqualTo("CUSTOMER");

        LoginRequest loginRequest = new LoginRequest(username, "SecurePass123!");
        LoginResponseBody login = client.post().uri("/api/login")
                .body(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponseBody.class)
                .returnResult()
                .getResponseBody();

        assertThat(login).isNotNull();
        assertThat(login.accessToken()).isNotBlank();

        UserResponse me = client.get().uri("/api/users/me")
                .header("Authorization", "Bearer " + login.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(me).isNotNull();
        assertThat(me.username()).isEqualTo(username);
    }

    @Test
    void protectedEndpoint_withoutAnyToken_isRejectedByTheRealSecurityFilterChain() {
        client.get().uri("/api/users/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpoint_withGarbageToken_isRejected() {
        client.get().uri("/api/users/me")
                .header("Authorization", "Bearer not-a-real-jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void register_withDuplicateUsername_returnsStandardErrorBodyViaGlobalExceptionHandler() {
        String username = uniqueUsername();
        RegisterRequest request = new RegisterRequest(username, "SecurePass123!", username + "@example.com");
        client.post().uri("/api/register").body(request).exchange().expectStatus().isCreated();

        ErrorResponse error = client.post().uri("/api/register")
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(error).isNotNull();
        assertThat(error.status()).isEqualTo(409);
        assertThat(error.error()).isEqualTo("USER_ALREADY_EXISTS");
        assertThat(error.timestamp()).isNotNull();
    }

    @Test
    void register_withBlankUsername_returns400WithFieldValidationDetails() {
        RegisterRequest invalid = new RegisterRequest("", "SecurePass123!", "someone@example.com");

        ErrorResponse error = client.post().uri("/api/register")
                .body(invalid)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(error).isNotNull();
        assertThat(error.error()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void login_withWrongPassword_returns401WithStandardErrorBody() {
        String username = uniqueUsername();
        client.post().uri("/api/register")
                .body(new RegisterRequest(username, "SecurePass123!", username + "@example.com"))
                .exchange()
                .expectStatus().isCreated();

        ErrorResponse error = client.post().uri("/api/login")
                .body(new LoginRequest(username, "WrongPassword!"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(error).isNotNull();
        assertThat(error.error()).isEqualTo("INVALID_CREDENTIALS");
    }

    private record LoginResponseBody(String accessToken, String tokenType, long expiresInSeconds) {
    }
}
