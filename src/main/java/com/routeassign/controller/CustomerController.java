package com.routeassign.controller;

import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.CustomerDetailsResponse;
import com.routeassign.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * GET /api/customers/{id}
     * Get customer profile by CustomerDetails ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDetailsResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getById(id)));
    }

    /**
     * GET /api/customers/auth/{authId}
     * Get customer profile by UserAuth ID.
     */
    @GetMapping("/auth/{authId}")
    public ResponseEntity<ApiResponse<CustomerDetailsResponse>> getByAuthId(@PathVariable Long authId) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getByAuthId(authId)));
    }

    /**
     * PATCH /api/customers/{id}/location
     * Update the default delivery location of a customer.
     */
    @PatchMapping("/{id}/location")
    public ResponseEntity<ApiResponse<CustomerDetailsResponse>> updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(customerService.updateLocation(id, request)));
    }
}
