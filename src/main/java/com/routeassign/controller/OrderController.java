package com.routeassign.controller;

import com.routeassign.domain.enums.OrderStatus;
import com.routeassign.dto.request.OrderRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.dto.response.OrderResponse;
import com.routeassign.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/orders?customerId={id}
     * Place a new order. Triggers the auto-assignment algorithm immediately.
     * Returns the resulting DeliveryAssignment (partner + ETA).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryAssignmentResponse>> placeOrder(
            @RequestParam Long customerId,
            @Valid @RequestBody OrderRequest request) {
        DeliveryAssignmentResponse response = orderService.placeOrder(customerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed and partner assigned", response));
    }

    /**
     * GET /api/orders/{orderId}
     * Get an order by its ID.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getById(orderId)));
    }

    /**
     * GET /api/orders/customer/{customerId}
     * Get all orders for a specific customer.
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByCustomerId(
            @PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getByCustomerId(customerId)));
    }

    /**
     * GET /api/orders/vendor/{vendorId}
     * Get all orders received by a specific vendor.
     */
    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByVendorId(
            @PathVariable Long vendorId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getByVendorId(vendorId)));
    }

    /**
     * GET /api/orders?status={status}
     * Get all orders filtered by status.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByStatus(
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getByStatus(status)));
    }

    /**
     * PATCH /api/orders/{orderId}/status?newStatus={status}
     * Update the status of an order.
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus newStatus) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateStatus(orderId, newStatus)));
    }
}
