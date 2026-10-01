package com.routeassign.controller;

import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.dto.response.ReassignResponse;
import com.routeassign.service.DeliveryAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * GET /api/assignments?status={status}
     * Returns all assignments with a given delivery status.
     * Used for the live deliveries feed on the admin/operations UI.
     * Omitting ?status returns all assignments regardless of status.
     *
     * Example — get every in-flight delivery:
     *   GET /api/assignments?status=EN_ROUTE_TO_CUSTOMER
     *
     * Response: 200 OK — List<DeliveryAssignmentResponse>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DeliveryAssignmentResponse>>> getByStatus(
            @RequestParam(required = false) DeliveryStatus status) {
        List<DeliveryAssignmentResponse> result = (status != null)
                ? deliveryAssignmentService.getByStatus(status)
                : deliveryAssignmentService.getByStatus(null);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * POST /api/assignments/{id}/reassign?reason={text}
     * Cancels the current assignment and immediately re-runs the auto-assignment
     * algorithm on the same order to find a replacement delivery partner.
     *
     * Use cases:
     *   - Partner no-show or unreachable
     *   - Failed delivery needing a second attempt
     *   - Admin manual override
     *
     * The old assignment is moved to CANCELLED (triggers history + weight restore).
     * A new DeliveryAssignment is created for the same order via the normal algorithm.
     *
     * Path param: id — the assignment ID to cancel and replace
     * Query param: reason (optional) — displayed in the response for audit purposes
     *
     * Response: 200 OK — ReassignResponse { oldAssignmentId, newAssignment, reason }
     * Error: 400 if the assignment is already in a terminal state (DELIVERED/CANCELLED/FAILED)
     * Error: 422 if no eligible partner is available for re-assignment
     */
    @PostMapping("/{id}/reassign")
    public ResponseEntity<ApiResponse<ReassignResponse>> reassign(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Manual reassignment") String reason) {
        return ResponseEntity.ok(
                ApiResponse.success("Reassignment successful",
                        deliveryAssignmentService.reassign(id, reason)));
    }
}
