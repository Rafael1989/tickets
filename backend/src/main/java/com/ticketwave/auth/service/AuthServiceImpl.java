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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthServiceImpl(
            UserRepository userRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateUserException("Email '" + request.email() + "' is already registered");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.CUSTOMER)
                .build();

        User saved = userRepository.save(user);
        auditService.record(saved.getUsername(), "USER_REGISTERED", "USER", saved.getId(), "role=CUSTOMER");
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(user.getUsername(), List.of(user.getRole().name()));
        return new LoginResponse(accessToken, "Bearer", jwtService.getAccessTokenTtlSeconds());
    }
}
