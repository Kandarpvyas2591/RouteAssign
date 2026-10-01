package com.routeassign.service.impl;

import com.routeassign.domain.entity.CustomerDetails;
import com.routeassign.domain.entity.UserAuth;
import com.routeassign.domain.entity.UserDetails;
import com.routeassign.domain.entity.VendorDetails;
import com.routeassign.domain.enums.UserRole;
import com.routeassign.dto.request.ChangePasswordRequest;
import com.routeassign.dto.request.LoginRequest;
import com.routeassign.dto.request.RegisterRequest;
import com.routeassign.dto.response.AuthResponse;
import com.routeassign.exception.BadRequestException;
import com.routeassign.exception.DuplicateResourceException;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.CustomerDetailsRepository;
import com.routeassign.repository.UserAuthRepository;
import com.routeassign.repository.UserDetailsRepository;
import com.routeassign.repository.VendorDetailsRepository;
import com.routeassign.security.JwtService;
import com.routeassign.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserAuthRepository        userAuthRepository;
    private final UserDetailsRepository     userDetailsRepository;
    private final CustomerDetailsRepository customerDetailsRepository;
    private final VendorDetailsRepository   vendorDetailsRepository;
    private final PasswordEncoder           passwordEncoder;
    private final JwtService                jwtService;

    // ── Register ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Uniqueness checks
        if (userAuthRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (userAuthRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }

        // 2. Persist UserAuth
        UserAuth auth = UserAuth.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .isActive(true)
                .build();
        auth = userAuthRepository.save(auth);

        // 3. Persist role-specific profile
        switch (request.getRole()) {
            case DELIVERY_PARTNER -> persistDeliveryPartnerProfile(auth, request);
            case CUSTOMER         -> persistCustomerProfile(auth, request);
            case VENDOR           -> persistVendorProfile(auth, request);
            case ADMIN            -> { /* no profile table needed for admin */ }
        }

        // 4. Generate JWT
        String token = jwtService.generateToken(auth.getEmail(), auth.getUserId(),
                auth.getRole().name());

        log.info("Registered new {} with email={}", request.getRole(), request.getEmail());
        return buildAuthResponse(token, auth);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Override
    public AuthResponse login(LoginRequest request) {
        UserAuth auth = userAuthRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        if (!auth.getIsActive()) {
            throw new BadRequestException("This account has been deactivated.");
        }

        if (!passwordEncoder.matches(request.getPassword(), auth.getPassword())) {
            throw new BadRequestException("Invalid email or password.");
        }

        String token = jwtService.generateToken(auth.getEmail(), auth.getUserId(),
                auth.getRole().name());

        log.info("Login successful for email={}", request.getEmail());
        return buildAuthResponse(token, auth);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void persistDeliveryPartnerProfile(UserAuth auth, RegisterRequest request) {
        UserDetails profile = UserDetails.builder()
                .auth(auth)
                .mobileNo(request.getMobileNo())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .capacity(20.0)               // default max capacity 20 kg — adjust as needed
                .currentAssignedWeight(0.0)
                .isActive(true)
                .isAvailable(true)
                .rating(0.0)
                .build();
        userDetailsRepository.save(profile);
    }

    private void persistCustomerProfile(UserAuth auth, RegisterRequest request) {
        CustomerDetails profile = CustomerDetails.builder()
                .auth(auth)
                .mobileNo(request.getMobileNo())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
        customerDetailsRepository.save(profile);
    }

    private void persistVendorProfile(UserAuth auth, RegisterRequest request) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new BadRequestException("Vendor registration requires latitude and longitude.");
        }
        VendorDetails profile = VendorDetails.builder()
                .auth(auth)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isActive(true)
                .build();
        vendorDetailsRepository.save(profile);
    }

    private AuthResponse buildAuthResponse(String token, UserAuth auth) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(auth.getUserId())
                .username(auth.getUsername())
                .email(auth.getEmail())
                .role(auth.getRole())
                .build();
    }

    // ── Change password ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserAuth auth = userAuthRepository.findById(request.getAuthUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", request.getAuthUserId()));

        if (!auth.getIsActive()) {
            throw new BadRequestException("This account has been deactivated.");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), auth.getPassword())) {
            throw new BadRequestException("Current password is incorrect.");
        }

        auth.setPassword(passwordEncoder.encode(request.getNewPassword()));
        auth.setPasswordResetAt(java.time.LocalDateTime.now());
        userAuthRepository.save(auth);

        log.info("Password changed for userId={}", auth.getUserId());
    }
}
