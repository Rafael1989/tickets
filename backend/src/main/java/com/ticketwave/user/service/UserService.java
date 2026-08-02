package com.ticketwave.user.service;

import com.ticketwave.user.dto.ChangePasswordRequest;
import com.ticketwave.user.dto.UpdateEmailRequest;
import com.ticketwave.user.dto.UserRequest;
import com.ticketwave.user.dto.UserResponse;
import com.ticketwave.user.entity.UserRole;

import java.util.List;

public interface UserService {

    /**
     * Admin-only. Lists every user account.
     */
    List<UserResponse> listUsers();

    /**
     * Admin-only.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if no such user exists
     */
    UserResponse getUser(Long userId);

    /**
     * Admin-only. Creates an account with an explicit role (operator,
     * support, admin, or customer) — the account provisioning path
     * self-registration deliberately doesn't offer. partnerId links an
     * OPERATOR account to the partner company it works for, so its
     * staff share one route/vehicle/driver inventory (see
     * catalog.security.TenantScope); it must be omitted for every other role.
     *
     * @throws com.ticketwave.auth.exception.DuplicateUserException if the username or email is already taken
     * @throws com.ticketwave.user.exception.PartnerAssignmentNotAllowedException if partnerId is set for a non-OPERATOR role
     * @throws com.ticketwave.partner.exception.PartnerNotFoundException if partnerId doesn't resolve to a partner
     */
    UserResponse createUser(String actorUsername, UserRequest request);

    /**
     * Admin-only. Reassigns an existing account's role. An admin cannot
     * change their own role, and the last remaining ADMIN account cannot be
     * demoted — both would risk locking every admin out of admin-only
     * endpoints with no recovery path short of direct DB access.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if no such user exists
     * @throws com.ticketwave.user.exception.SelfRoleChangeException if userId is the caller's own account
     * @throws com.ticketwave.user.exception.LastAdminDemotionException if userId is the last remaining ADMIN and role isn't ADMIN
     */
    UserResponse updateRole(String actorUsername, Long userId, UserRole role);

    /**
     * Self-service: any authenticated caller may fetch their own account.
     * Unlike {@link #getUser(Long)}, this isn't admin-gated — the caller's
     * own username (from the JWT) is the only thing that can be looked up.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if the username doesn't resolve to a user
     */
    UserResponse getCurrentUser(String username);

    /**
     * Self-service: changes the authenticated caller's own email.
     *
     * @throws com.ticketwave.auth.exception.DuplicateUserException if another account already uses that email
     */
    UserResponse updateCurrentEmail(String username, UpdateEmailRequest request);

    /**
     * Self-service: changes the authenticated caller's own password,
     * requiring the current password as proof of intent (a valid JWT alone
     * isn't treated as sufficient authorization for a credential change).
     *
     * @throws com.ticketwave.auth.exception.IncorrectPasswordException if currentPassword doesn't match
     */
    void changeCurrentPassword(String username, ChangePasswordRequest request);
}
