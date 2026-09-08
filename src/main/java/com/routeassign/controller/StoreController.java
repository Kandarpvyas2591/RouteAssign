package com.routeassign.controller;

import com.routeassign.dto.request.StoreRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.StoreResponse;
import com.routeassign.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendors/{vendorId}/store")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    /**
     * POST /api/vendors/{vendorId}/store
     * Add a new item to, or update quantity in, a vendor's store.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> addOrUpdateStock(
            @PathVariable Long vendorId,
            @Valid @RequestBody StoreRequest request) {
        StoreResponse response = storeService.addOrUpdateStock(vendorId, request);
        return ResponseEntity.ok(ApiResponse.success("Stock updated", response));
    }

    /**
     * GET /api/vendors/{vendorId}/store
     * Get all stock entries for a vendor.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreResponse>>> getByVendorId(
            @PathVariable Long vendorId) {
        return ResponseEntity.ok(ApiResponse.success(storeService.getByVendorId(vendorId)));
    }

    /**
     * GET /api/vendors/{vendorId}/store/{storeId}
     * Get a single store entry by its ID.
     */
    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreResponse>> getById(
            @PathVariable Long vendorId,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(ApiResponse.success(storeService.getById(storeId)));
    }

    /**
     * DELETE /api/vendors/{vendorId}/store/items/{itemId}
     * Remove an item from the vendor's store.
     */
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> removeStock(
            @PathVariable Long vendorId,
            @PathVariable Long itemId) {
        storeService.removeStock(vendorId, itemId);
        return ResponseEntity.ok(ApiResponse.success("Stock entry removed", null));
    }
}
