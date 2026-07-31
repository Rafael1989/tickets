package com.ticketwave.auth.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.dto.LoginRequest;
import com.ticketwave.auth.dto.LoginResponse;
import com.ticketwave.auth.dto.RegisterRequest;
import com.ticketwave.auth.exception.DuplicateUserException;
import com.ticketwave.auth.exception.InvalidCredentialsException;
import com.ticketwave.user.dto.UserResponse;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserRole;
import com.ticketwave.user.mapper.UserMapper;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void register_whenUsernameAndEmailAvailable_createsCustomerWithHashedPassword() {
        RegisterRequest request = new RegisterRequest("alice", "password123", "alice@example.com");
        given(userRepository.existsByUsername("alice")).willReturn(false);
        given(userRepository.existsByEmail("alice@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("hashed-password");

        User saved = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hashed-password")
                .role(UserRole.CUSTOMER)
                .createdAt(Instant.now())
                .build();
        given(userRepository.save(any(User.class))).willReturn(saved);

        UserResponse expectedResponse = new UserResponse(1L, "alice", "alice@example.com", UserRole.CUSTOMER, saved.getCreatedAt());
        given(userMapper.toResponse(saved)).willReturn(expectedResponse);

        UserResponse result = authService.register(request);

        assertThat(result).isEqualTo(expectedResponse);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User persisted = captor.getValue();
        assertThat(persisted.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(persisted.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(persisted.getUsername()).isEqualTo("alice");

        verify(auditService).record("alice", "USER_REGISTERED", "USER", 1L, "role=CUSTOMER");
    }

    @Test
    void register_whenUsernameTaken_throwsDuplicateUserExceptionAndNeverSaves() {
        given(userRepository.existsByUsername("alice")).willReturn(true);

        RegisterRequest request = new RegisterRequest("alice", "password123", "alice@example.com");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_whenEmailTaken_throwsDuplicateUserExceptionAndNeverSaves() {
        given(userRepository.existsByUsername("alice")).willReturn(false);
        given(userRepository.existsByEmail("alice@example.com")).willReturn(true);

        RegisterRequest request = new RegisterRequest("alice", "password123", "alice@example.com");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_whenCredentialsValid_returnsAccessToken() {
        User user = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hashed-password")
                .role(UserRole.CUSTOMER)
                .build();
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "hashed-password")).willReturn(true);
        given(jwtService.generateAccessToken("alice", List.of("CUSTOMER"))).willReturn("jwt-token");
        given(jwtService.getAccessTokenTtlSeconds()).willReturn(900L);

        LoginResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
    }

    @Test
    void login_whenUserNotFound_throwsInvalidCredentialsException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_whenPasswordWrong_throwsInvalidCredentialsException() {
        User user = User.builder()
                .id(1L)
                .username("alice")
                .passwordHash("hashed-password")
                .role(UserRole.CUSTOMER)
                .build();
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "hashed-password")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
