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
     * Optional ?status= filter for the customer's "My Orders" tabs.
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByCustomerId(
            @PathVariable Long customerId,
            @RequestParam(required = false) OrderStatus status) {
        List<OrderResponse> result = (status != null)
                ? orderService.getByCustomerIdAndStatus(customerId, status)
                : orderService.getByCustomerId(customerId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * GET /api/orders/vendor/{vendorId}
     * Get all orders received by a specific vendor.
     * Optional ?status= filter for the vendor portal's order management screen.
     */
    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByVendorId(
            @PathVariable Long vendorId,
            @RequestParam(required = false) OrderStatus status) {
        List<OrderResponse> result = (status != null)
                ? orderService.getByVendorIdAndStatus(vendorId, status)
                : orderService.getByVendorId(vendorId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * GET /api/orders
     * Get all orders, optionally filtered by ?status=.
     * Omitting ?status returns every order in the system (admin view).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByStatus(
            @RequestParam(required = false) OrderStatus status) {
        List<OrderResponse> result = (status != null)
                ? orderService.getByStatus(status)
                : orderService.getByStatus(null);
        return ResponseEntity.ok(ApiResponse.success(result));
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
