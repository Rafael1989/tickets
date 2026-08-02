package com.ticketwave.user.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.auth.exception.DuplicateUserException;
import com.ticketwave.auth.exception.IncorrectPasswordException;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.partner.exception.PartnerNotFoundException;
import com.ticketwave.partner.repository.PartnerRepository;
import com.ticketwave.user.dto.ChangePasswordRequest;
import com.ticketwave.user.dto.UpdateEmailRequest;
import com.ticketwave.user.dto.UserRequest;
import com.ticketwave.user.dto.UserResponse;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserRole;
import com.ticketwave.user.exception.LastAdminDemotionException;
import com.ticketwave.user.exception.PartnerAssignmentNotAllowedException;
import com.ticketwave.user.exception.SelfRoleChangeException;
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

    @Mock
    private PartnerRepository partnerRepository;

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

        UserResponse aliceResponse = new UserResponse(1L, "alice", "alice@example.com", UserRole.CUSTOMER, null, alice.getCreatedAt());
        UserResponse opResponse = new UserResponse(2L, "operator1", "operator1@example.com", UserRole.OPERATOR, null, op.getCreatedAt());
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
        UserResponse response = new UserResponse(1L, "alice", "alice@example.com", UserRole.CUSTOMER, null, alice.getCreatedAt());
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
        UserRequest request = new UserRequest("operator2", "password123", "operator2@example.com", UserRole.OPERATOR, null);
        User mapped = User.builder().username("operator2").email("operator2@example.com").role(UserRole.OPERATOR).build();
        User saved = user(3L, "operator2", UserRole.OPERATOR);

        given(userRepository.existsByUsername("operator2")).willReturn(false);
        given(userRepository.existsByEmail("operator2@example.com")).willReturn(false);
        given(userMapper.toEntity(request)).willReturn(mapped);
        given(passwordEncoder.encode("password123")).willReturn("hashed-password");
        given(userRepository.save(mapped)).willReturn(saved);
        UserResponse response = new UserResponse(3L, "operator2", "operator2@example.com", UserRole.OPERATOR, null, saved.getCreatedAt());
        given(userMapper.toResponse(saved)).willReturn(response);

        UserResponse result = userService.createUser("admin1", request);

        assertThat(result).isEqualTo(response);
        assertThat(mapped.getPasswordHash()).isEqualTo("hashed-password");
        verify(auditService).record("admin1", "USER_CREATED", "USER", 3L, "role=OPERATOR");
    }

    @Test
    void createUser_whenUsernameTaken_throwsDuplicateUserExceptionAndNeverSaves() {
        given(userRepository.existsByUsername("operator2")).willReturn(true);
        UserRequest request = new UserRequest("operator2", "password123", "operator2@example.com", UserRole.OPERATOR, null);

        assertThatThrownBy(() -> userService.createUser("admin1", request))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_whenEmailTaken_throwsDuplicateUserExceptionAndNeverSaves() {
        given(userRepository.existsByUsername("operator2")).willReturn(false);
        given(userRepository.existsByEmail("operator2@example.com")).willReturn(true);
        UserRequest request = new UserRequest("operator2", "password123", "operator2@example.com", UserRole.OPERATOR, null);

        assertThatThrownBy(() -> userService.createUser("admin1", request))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_withPartnerIdForOperatorRole_linksThePartner() {
        Partner partner = Partner.builder().id(9L).name("Acme Transit").build();
        UserRequest request = new UserRequest("operator2", "password123", "operator2@example.com", UserRole.OPERATOR, 9L);
        User mapped = User.builder().username("operator2").email("operator2@example.com").role(UserRole.OPERATOR).build();
        User saved = user(3L, "operator2", UserRole.OPERATOR);

        given(userRepository.existsByUsername("operator2")).willReturn(false);
        given(userRepository.existsByEmail("operator2@example.com")).willReturn(false);
        given(userMapper.toEntity(request)).willReturn(mapped);
        given(partnerRepository.findById(9L)).willReturn(Optional.of(partner));
        given(passwordEncoder.encode("password123")).willReturn("hashed-password");
        given(userRepository.save(mapped)).willReturn(saved);
        given(userMapper.toResponse(saved)).willReturn(
                new UserResponse(3L, "operator2", "operator2@example.com", UserRole.OPERATOR, 9L, saved.getCreatedAt()));

        userService.createUser("admin1", request);

        assertThat(mapped.getPartner()).isEqualTo(partner);
    }

    @Test
    void createUser_withPartnerIdForNonOperatorRole_throwsPartnerAssignmentNotAllowedException() {
        UserRequest request = new UserRequest("support2", "password123", "support2@example.com", UserRole.SUPPORT, 9L);

        assertThatThrownBy(() -> userService.createUser("admin1", request))
                .isInstanceOf(PartnerAssignmentNotAllowedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_withUnknownPartnerId_throwsPartnerNotFoundException() {
        UserRequest request = new UserRequest("operator2", "password123", "operator2@example.com", UserRole.OPERATOR, 99L);
        User mapped = User.builder().username("operator2").email("operator2@example.com").role(UserRole.OPERATOR).build();

        given(userRepository.existsByUsername("operator2")).willReturn(false);
        given(userRepository.existsByEmail("operator2@example.com")).willReturn(false);
        given(userMapper.toEntity(request)).willReturn(mapped);
        given(passwordEncoder.encode("password123")).willReturn("hashed-password");
        given(partnerRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createUser("admin1", request))
                .isInstanceOf(PartnerNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRole_whenFound_changesRoleAndAudits() {
        User alice = user(1L, "alice", UserRole.CUSTOMER);
        given(userRepository.findById(1L)).willReturn(Optional.of(alice));
        given(userMapper.toResponse(alice)).willReturn(
                new UserResponse(1L, "alice", "alice@example.com", UserRole.SUPPORT, null, alice.getCreatedAt()));

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

    @Test
    void updateRole_whenTargetingOwnAccount_throwsSelfRoleChangeExceptionAndNeverAudits() {
        User admin = user(1L, "admin1", UserRole.ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.updateRole("admin1", 1L, UserRole.SUPPORT))
                .isInstanceOf(SelfRoleChangeException.class);

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void updateRole_whenDemotingTheLastAdmin_throwsLastAdminDemotionExceptionAndNeverAudits() {
        User lastAdmin = user(2L, "admin2", UserRole.ADMIN);
        given(userRepository.findById(2L)).willReturn(Optional.of(lastAdmin));
        given(userRepository.countByRole(UserRole.ADMIN)).willReturn(1L);

        assertThatThrownBy(() -> userService.updateRole("admin1", 2L, UserRole.SUPPORT))
                .isInstanceOf(LastAdminDemotionException.class);

        assertThat(lastAdmin.getRole()).isEqualTo(UserRole.ADMIN);
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void updateRole_whenDemotingOneOfSeveralAdmins_isAllowed() {
        User anotherAdmin = user(2L, "admin2", UserRole.ADMIN);
        given(userRepository.findById(2L)).willReturn(Optional.of(anotherAdmin));
        given(userRepository.countByRole(UserRole.ADMIN)).willReturn(2L);
        given(userMapper.toResponse(anotherAdmin)).willReturn(
                new UserResponse(2L, "admin2", "admin2@example.com", UserRole.SUPPORT, null, anotherAdmin.getCreatedAt()));

        UserResponse result = userService.updateRole("admin1", 2L, UserRole.SUPPORT);

        assertThat(result.role()).isEqualTo(UserRole.SUPPORT);
        verify(auditService).record("admin1", "USER_ROLE_CHANGED", "USER", 2L, "ADMIN -> SUPPORT");
    }

    @Test
    void updateRole_whenReassigningTheLastAdminToAdminAgain_isAllowed() {
        User lastAdmin = user(2L, "admin2", UserRole.ADMIN);
        given(userRepository.findById(2L)).willReturn(Optional.of(lastAdmin));
        given(userMapper.toResponse(lastAdmin)).willReturn(
                new UserResponse(2L, "admin2", "admin2@example.com", UserRole.ADMIN, null, lastAdmin.getCreatedAt()));

        UserResponse result = userService.updateRole("admin1", 2L, UserRole.ADMIN);

        assertThat(result.role()).isEqualTo(UserRole.ADMIN);
        verify(userRepository, never()).countByRole(any());
    }

    @Test
    void getCurrentUser_whenFound_returnsMappedResponse() {
        User alice = user(1L, "alice", UserRole.CUSTOMER);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        UserResponse response = new UserResponse(1L, "alice", "alice@example.com", UserRole.CUSTOMER, null, alice.getCreatedAt());
        given(userMapper.toResponse(alice)).willReturn(response);

        assertThat(userService.getCurrentUser("alice")).isEqualTo(response);
    }

    @Test
    void getCurrentUser_whenMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser("ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateCurrentEmail_whenNewEmailIsAvailable_updatesAndReturnsResponse() {
        User alice = user(1L, "alice", UserRole.CUSTOMER);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        given(userRepository.existsByEmail("new@example.com")).willReturn(false);
        given(userMapper.toResponse(alice)).willReturn(
                new UserResponse(1L, "alice", "new@example.com", UserRole.CUSTOMER, null, alice.getCreatedAt()));

        UserResponse result = userService.updateCurrentEmail("alice", new UpdateEmailRequest("new@example.com"));

        assertThat(alice.getEmail()).isEqualTo("new@example.com");
        assertThat(result.email()).isEqualTo("new@example.com");
    }

    @Test
    void updateCurrentEmail_whenResubmittingOwnCurrentEmail_doesNotTreatItAsDuplicate() {
        User alice = user(1L, "alice", UserRole.CUSTOMER);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        given(userMapper.toResponse(alice)).willReturn(
                new UserResponse(1L, "alice", "alice@example.com", UserRole.CUSTOMER, null, alice.getCreatedAt()));

        userService.updateCurrentEmail("alice", new UpdateEmailRequest("alice@example.com"));

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updateCurrentEmail_whenEmailTakenBySomeoneElse_throwsDuplicateUserException() {
        User alice = user(1L, "alice", UserRole.CUSTOMER);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        given(userRepository.existsByEmail("taken@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.updateCurrentEmail("alice", new UpdateEmailRequest("taken@example.com")))
                .isInstanceOf(DuplicateUserException.class);

        assertThat(alice.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void changeCurrentPassword_whenCurrentPasswordMatches_updatesHash() {
        User alice = user(1L, "alice", UserRole.CUSTOMER);
        alice.setPasswordHash("old-hash");
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        given(passwordEncoder.matches("old-pass", "old-hash")).willReturn(true);
        given(passwordEncoder.encode("new-password123")).willReturn("new-hash");

        userService.changeCurrentPassword("alice", new ChangePasswordRequest("old-pass", "new-password123"));

        assertThat(alice.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void changeCurrentPassword_whenCurrentPasswordWrong_throwsIncorrectPasswordExceptionAndLeavesHashUnchanged() {
        User alice = user(1L, "alice", UserRole.CUSTOMER);
        alice.setPasswordHash("old-hash");
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        given(passwordEncoder.matches("wrong-pass", "old-hash")).willReturn(false);

        assertThatThrownBy(() -> userService.changeCurrentPassword("alice", new ChangePasswordRequest("wrong-pass", "new-password123")))
                .isInstanceOf(IncorrectPasswordException.class);

        assertThat(alice.getPasswordHash()).isEqualTo("old-hash");
    }
}
