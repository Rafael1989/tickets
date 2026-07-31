package com.ticketwave.user.service;

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
     * support, admin, or customer) — the "partner onboarding" / account
     * provisioning path self-registration deliberately doesn't offer.
     *
     * @throws com.ticketwave.auth.exception.DuplicateUserException if the username or email is already taken
     */
    UserResponse createUser(String actorUsername, UserRequest request);

    /**
     * Admin-only. Reassigns an existing account's role.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if no such user exists
     */
    UserResponse updateRole(String actorUsername, Long userId, UserRole role);
}
