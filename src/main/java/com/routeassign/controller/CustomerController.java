package com.routeassign.controller;

import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.CustomerDetailsResponse;
import com.routeassign.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * GET /api/customers
     * List all customers in the system (admin view).
     *
     * Response: 200 OK — List<CustomerDetailsResponse>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerDetailsResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(customerService.getAll()));
    }

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

    /**
     * DELETE /api/customers/{id}
     * Soft-deactivates a customer account.
     * Sets isActive = false on UserAuth — the account can no longer log in.
     *
     * Response: 200 OK — "Customer deactivated"
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        customerService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success("Customer deactivated", null));
    }
}
