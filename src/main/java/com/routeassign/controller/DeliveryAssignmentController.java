package com.routeassign.controller;

import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.service.DeliveryAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class DeliveryAssignmentController {

    private final DeliveryAssignmentService deliveryAssignmentService;

    /**
     * GET /api/assignments/{id}
     * Get a delivery assignment by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeliveryAssignmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(deliveryAssignmentService.getById(id)));
    }

    /**
     * GET /api/assignments/order/{orderId}
     * Get the assignment for a specific order.
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<DeliveryAssignmentResponse>> getByOrderId(
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(deliveryAssignmentService.getByOrderId(orderId)));
    }

    /**
     * GET /api/assignments/partner/{partnerId}
     * Get all assignments for a specific delivery partner.
     */
    @GetMapping("/partner/{partnerId}")
    public ResponseEntity<ApiResponse<List<DeliveryAssignmentResponse>>> getByPartnerId(
            @PathVariable Long partnerId) {
        return ResponseEntity.ok(ApiResponse.success(deliveryAssignmentService.getByPartnerId(partnerId)));
    }

    /**
     * GET /api/assignments/vendor/{vendorId}
     * Get all assignments for a specific vendor.
     */
    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<ApiResponse<List<DeliveryAssignmentResponse>>> getByVendorId(
            @PathVariable Long vendorId) {
        return ResponseEntity.ok(ApiResponse.success(deliveryAssignmentService.getByVendorId(vendorId)));
    }

    /**
     * PATCH /api/assignments/{id}/status?newStatus={status}
     * Update the delivery status of an assignment.
     * When set to DELIVERED/CANCELLED/FAILED, history records are automatically created.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DeliveryAssignmentResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam DeliveryStatus newStatus) {
        return ResponseEntity.ok(
                ApiResponse.success(deliveryAssignmentService.updateStatus(id, newStatus)));
    }
}
