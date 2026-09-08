package com.routeassign.controller;

import com.routeassign.dto.request.UpdateAvailabilityRequest;
import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.UserDetailsResponse;
import com.routeassign.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /api/users/{id}
     * Get a delivery partner profile by UserDetails ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDetailsResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getById(id)));
    }

    /**
     * GET /api/users/auth/{authId}
     * Get a delivery partner profile by UserAuth ID.
     */
    @GetMapping("/auth/{authId}")
    public ResponseEntity<ApiResponse<UserDetailsResponse>> getByAuthId(@PathVariable Long authId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getByAuthId(authId)));
    }

    /**
     * GET /api/users/delivery-partners
     * Get all active delivery partners.
     */
    @GetMapping("/delivery-partners")
    public ResponseEntity<ApiResponse<List<UserDetailsResponse>>> getAllActiveDeliveryPartners() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllActiveDeliveryPartners()));
    }

    /**
     * PATCH /api/users/{id}/location
     * Update the home location of a delivery partner.
     */
    @PatchMapping("/{id}/location")
    public ResponseEntity<ApiResponse<UserDetailsResponse>> updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateLocation(id, request)));
    }

    /**
     * PATCH /api/users/{id}/availability
     * Toggle the availability flag of a delivery partner.
     */
    @PatchMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<UserDetailsResponse>> updateAvailability(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAvailabilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateAvailability(id, request)));
    }

    /**
     * DELETE /api/users/{id}
     * Deactivate (soft-delete) a user account.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        userService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success("User deactivated", null));
    }
}
