package com.routeassign.service;

import com.routeassign.dto.request.OrderRequest;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.dto.response.OrderResponse;
import com.routeassign.domain.enums.OrderStatus;

import java.util.List;

public interface OrderService {

    /**
     * Places a new order on behalf of the authenticated customer.
     * After persisting the order, immediately triggers the auto-assignment algorithm
     * and returns the resulting delivery assignment.
     *
     * @param customerId the ID of the placing customer (UserDetails ID)
     * @param request    order payload
     * @return the created DeliveryAssignment including the selected partner and ETA
     */
    DeliveryAssignmentResponse placeOrder(Long customerId, OrderRequest request);

    /**
     * Returns an order by its ID.
     */
    OrderResponse getById(Long orderId);

    /**
     * Returns all orders placed by a specific customer.
     */
    List<OrderResponse> getByCustomerId(Long customerId);

    /**
     * Returns all orders received by a specific vendor.
     */
    List<OrderResponse> getByVendorId(Long vendorId);

    /**
     * Returns all orders with a given status.
     */
    List<OrderResponse> getByStatus(OrderStatus status);

    /**
     * Updates the status of an order (e.g. PICKED_UP, DELIVERED, CANCELLED).
     */
    OrderResponse updateStatus(Long orderId, OrderStatus newStatus);
}
