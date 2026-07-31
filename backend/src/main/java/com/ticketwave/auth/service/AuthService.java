package com.ticketwave.auth.service;

import com.ticketwave.auth.dto.LoginRequest;
import com.ticketwave.auth.dto.LoginResponse;
import com.ticketwave.auth.dto.RegisterRequest;
import com.ticketwave.user.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
