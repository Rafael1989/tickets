package com.ticketwave.user.controller;

import com.ticketwave.user.dto.ChangePasswordRequest;
import com.ticketwave.user.dto.RoleUpdateRequest;
import com.ticketwave.user.dto.UpdateEmailRequest;
import com.ticketwave.user.dto.UserRequest;
import com.ticketwave.user.dto.UserResponse;
import com.ticketwave.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Requires a bearer JWT. GET /me works for any authenticated caller; every other endpoint requires the ADMIN role.")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated caller's own account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's account"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    })
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentUser(authentication.getName()));
    }

    @PutMapping("/me/email")
    @Operation(summary = "Change the authenticated caller's own email")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "409", description = "Email already in use by another account")
    })
    public ResponseEntity<UserResponse> updateCurrentEmail(
            Authentication authentication,
            @Valid @RequestBody UpdateEmailRequest request
    ) {
        return ResponseEntity.ok(userService.updateCurrentEmail(authentication.getName(), request));
    }

    @PutMapping("/me/password")
    @Operation(
            summary = "Change the authenticated caller's own password",
            description = "Requires the current password, even though the caller is already authenticated via JWT."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid bearer token, or currentPassword is wrong")
    })
    public ResponseEntity<Void> changeCurrentPassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changeCurrentPassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List every user account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User accounts"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userService.listUsers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user account by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "No such user")
    })
    public ResponseEntity<UserResponse> getUser(@PathVariable("id") Long userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @PostMapping
    @Operation(
            summary = "Create an account with an explicit role",
            description = "The account-provisioning path for operator/support/admin accounts, since self-registration always creates a CUSTOMER."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "409", description = "Username or email already in use")
    })
    public ResponseEntity<UserResponse> createUser(
            Authentication authentication,
            @Valid @RequestBody UserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(authentication.getName(), request));
    }

    @PutMapping("/{id}/role")
    @Operation(
            summary = "Reassign a user's role",
            description = "An admin cannot change their own role, and the last remaining ADMIN account cannot be demoted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "No such user"),
            @ApiResponse(responseCode = "409", description = "Target is the caller's own account, or the last remaining ADMIN")
    })
    public ResponseEntity<UserResponse> updateRole(
            Authentication authentication,
            @PathVariable("id") Long userId,
            @Valid @RequestBody RoleUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateRole(authentication.getName(), userId, request.role()));
    }
}
