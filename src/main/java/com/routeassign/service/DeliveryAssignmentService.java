package com.routeassign.service;

import com.routeassign.domain.entity.Order;
import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.dto.response.DeliveryAssignmentResponse;

import java.util.List;

public interface DeliveryAssignmentService {

    /**
     * Entry point for the auto-assignment algorithm.
     * Selects the best eligible delivery partner for the given order and
     * persists the DeliveryAssignment record.
     *
     * The algorithm applies (in order):
     *  1. Eligibility filter  (active, available, sufficient capacity)
     *  2. Same-vendor reuse   (Section 5 / 6)
     *  3. Haversine distances (Section 3)
     *  4. Tie-breaking        (Section 7: never-assigned → random → best rating → longest idle)
     *  5. Working-hour rules  (Section 8 / 9 / 10)
     *  6. ETA calculation     (Section 11)
     *
     * @param order the newly placed order
     * @return the persisted DeliveryAssignmentResponse
     */
    DeliveryAssignmentResponse assign(Order order);

    /**
     * Returns a delivery assignment by its ID.
     */
    DeliveryAssignmentResponse getById(Long id);

    /**
     * Returns the assignment for a specific order.
     */
    DeliveryAssignmentResponse getByOrderId(Long orderId);

    /**
     * Returns all assignments for a specific delivery partner.
     */
    List<DeliveryAssignmentResponse> getByPartnerId(Long partnerId);

    /**
     * Returns all assignments for a specific vendor.
     */
    List<DeliveryAssignmentResponse> getByVendorId(Long vendorId);

    /**
     * Updates the delivery status of an assignment (e.g. EN_ROUTE_TO_VENDOR → COLLECTED).
     */
    DeliveryAssignmentResponse updateStatus(Long id, DeliveryStatus newStatus);
}
