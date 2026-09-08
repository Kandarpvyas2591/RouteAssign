package com.routeassign.controller;

import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.VendorDetailsResponse;
import com.routeassign.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    /**
     * GET /api/vendors/{id}
     * Get vendor profile by VendorDetails ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VendorDetailsResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(vendorService.getById(id)));
    }

    /**
     * GET /api/vendors/auth/{authId}
     * Get vendor profile by UserAuth ID.
     */
    @GetMapping("/auth/{authId}")
    public ResponseEntity<ApiResponse<VendorDetailsResponse>> getByAuthId(@PathVariable Long authId) {
        return ResponseEntity.ok(ApiResponse.success(vendorService.getByAuthId(authId)));
    }

    /**
     * GET /api/vendors
     * Get all active vendors.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<VendorDetailsResponse>>> getAllActive() {
        return ResponseEntity.ok(ApiResponse.success(vendorService.getAllActive()));
    }

    /**
     * PATCH /api/vendors/{id}/location
     * Update the physical location of a vendor.
     */
    @PatchMapping("/{id}/location")
    public ResponseEntity<ApiResponse<VendorDetailsResponse>> updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vendorService.updateLocation(id, request)));
    }

    /**
     * DELETE /api/vendors/{id}
     * Deactivate (soft-delete) a vendor.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        vendorService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success("Vendor deactivated", null));
    }
}
