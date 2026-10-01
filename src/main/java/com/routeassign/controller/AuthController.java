package com.routeassign.controller;

import com.routeassign.dto.request.ChangePasswordRequest;
import com.routeassign.dto.request.LoginRequest;
import com.routeassign.dto.request.RegisterRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.AuthResponse;
import com.routeassign.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/register
     * Register a new account (customer, delivery partner, vendor).
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    /**
     * POST /api/auth/login
     * Authenticate and receive a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    /**
     * PATCH /api/auth/password
     * Change the password for an existing account.
     *
     * The caller must supply:
     *   - authUserId   — the UserAuth.userId of the account to update
     *   - currentPassword — verified against the stored hash before accepting the change
     *   - newPassword     — min 8 characters; replaces the current password
     *
     * On success, passwordResetAt is updated on the UserAuth record.
     *
     * Response: 200 OK — "Password changed successfully"
     * Error: 400 — current password incorrect
     * Error: 400 — account deactivated
     * Error: 404 — user not found
     */
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }
}
