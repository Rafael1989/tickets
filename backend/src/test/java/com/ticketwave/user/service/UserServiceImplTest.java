package com.ticketwave.user.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.auth.exception.DuplicateUserException;
import com.ticketwave.user.dto.UserRequest;
import com.ticketwave.user.dto.UserResponse;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserRole;
import com.ticketwave.user.exception.UserNotFoundException;
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
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserServiceImpl userService;

    private static User user(long id, String username, UserRole role) {
        return User.builder().id(id).username(username).email(username + "@example.com")
                .role(role).createdAt(Instant.now()).build();
    }

    @Test
    void listUsers_returnsEveryUserMappedToResponse() {
        User alice = user(1L, "alice", UserRole.CUSTOMER);
        User op = user(2L, "operator1", UserRole.OPERATOR);
        given(userRepository.findAll()).willReturn(List.of(alice, op));

        UserResponse aliceResponse = new UserResponse(1L, "alice", "alice@example.com", UserRole.CUSTOMER, alice.getCreatedAt());
        UserResponse opResponse = new UserResponse(2L, "operator1", "operator1@example.com", UserRole.OPERATOR, op.getCreatedAt());
        given(userMapper.toResponse(alice)).willReturn(aliceResponse);
        given(userMapper.toResponse(op)).willReturn(opResponse);

        List<UserResponse> result = userService.listUsers();

        assertThat(result).containsExactly(aliceResponse, opResponse);
    }

    @Test
    void listUsers_whenNoUsers_returnsEmptyList() {
        given(userRepository.findAll()).willReturn(List.of());

        assertThat(userService.listUsers()).isEmpty();
    }

    @Test
    void getUser_whenFound_returnsMappedResponse() {
        User alice = user(1L, "alice", UserRole.CUSTOMER);
        given(userRepository.findById(1L)).willReturn(Optional.of(alice));
        UserResponse response = new UserResponse(1L, "alice", "alice@example.com", UserRole.CUSTOMER, alice.getCreatedAt());
        given(userMapper.toResponse(alice)).willReturn(response);

        assertThat(userService.getUser(1L)).isEqualTo(response);
    }

    @Test
    void getUser_whenMissing_throwsUserNotFoundException() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void createUser_whenUsernameAndEmailAvailable_createsWithHashedPasswordAndAudits() {
        UserRequest request = new UserRequest("operator2", "password123", "operator2@example.com", UserRole.OPERATOR);
        User mapped = User.builder().username("operator2").email("operator2@example.com").role(UserRole.OPERATOR).build();
        User saved = user(3L, "operator2", UserRole.OPERATOR);

        given(userRepository.existsByUsername("operator2")).willReturn(false);
        given(userRepository.existsByEmail("operator2@example.com")).willReturn(false);
        given(userMapper.toEntity(request)).willReturn(mapped);
        given(passwordEncoder.encode("password123")).willReturn("hashed-password");
        given(userRepository.save(mapped)).willReturn(saved);
        UserResponse response = new UserResponse(3L, "operator2", "operator2@example.com", UserRole.OPERATOR, saved.getCreatedAt());
        given(userMapper.toResponse(saved)).willReturn(response);

        UserResponse result = userService.createUser("admin1", request);

        assertThat(result).isEqualTo(response);
        assertThat(mapped.getPasswordHash()).isEqualTo("hashed-password");
        verify(auditService).record("admin1", "USER_CREATED", "USER", 3L, "role=OPERATOR");
    }

    @Test
    void createUser_whenUsernameTaken_throwsDuplicateUserExceptionAndNeverSaves() {
        given(userRepository.existsByUsername("operator2")).willReturn(true);
        UserRequest request = new UserRequest("operator2", "password123", "operator2@example.com", UserRole.OPERATOR);

        assertThatThrownBy(() -> userService.createUser("admin1", request))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_whenEmailTaken_throwsDuplicateUserExceptionAndNeverSaves() {
        given(userRepository.existsByUsername("operator2")).willReturn(false);
        given(userRepository.existsByEmail("operator2@example.com")).willReturn(true);
        UserRequest request = new UserRequest("operator2", "password123", "operator2@example.com", UserRole.OPERATOR);

        assertThatThrownBy(() -> userService.createUser("admin1", request))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRole_whenFound_changesRoleAndAudits() {
        User alice = user(1L, "alice", UserRole.CUSTOMER);
        given(userRepository.findById(1L)).willReturn(Optional.of(alice));
        given(userMapper.toResponse(alice)).willReturn(
                new UserResponse(1L, "alice", "alice@example.com", UserRole.SUPPORT, alice.getCreatedAt()));

        UserResponse result = userService.updateRole("admin1", 1L, UserRole.SUPPORT);

        assertThat(alice.getRole()).isEqualTo(UserRole.SUPPORT);
        assertThat(result.role()).isEqualTo(UserRole.SUPPORT);
        verify(auditService).record("admin1", "USER_ROLE_CHANGED", "USER", 1L, "CUSTOMER -> SUPPORT");
    }

    @Test
    void updateRole_whenMissing_throwsUserNotFoundException() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateRole("admin1", 99L, UserRole.SUPPORT))
                .isInstanceOf(UserNotFoundException.class);
    }
}
