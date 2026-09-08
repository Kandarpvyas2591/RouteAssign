package com.routeassign.service;

import com.routeassign.dto.request.LoginRequest;
import com.routeassign.dto.request.RegisterRequest;
import com.routeassign.dto.response.AuthResponse;

public interface AuthService {

    /**
     * Registers a new user account (any role) and returns a JWT token.
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates a user by email and password, returns a JWT token.
     */
    AuthResponse login(LoginRequest request);
}
