package com.routeassign.service;

import com.routeassign.domain.entity.Order;
import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.dto.response.ReassignResponse;

import java.util.List;

public interface DeliveryAssignmentService {

    /**
     * Entry point for the auto-assignment algorithm.
     * Selects the best eligible delivery partner for the given order and
     * persists the DeliveryAssignment record.
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
     * Returns all assignments with a given delivery status.
     * Used for the live deliveries feed: GET /api/assignments?status=EN_ROUTE_TO_CUSTOMER
     */
    List<DeliveryAssignmentResponse> getByStatus(DeliveryStatus status);

    /**
     * Updates the delivery status of an assignment (e.g. EN_ROUTE_TO_VENDOR → COLLECTED).
     * When set to DELIVERED/CANCELLED/FAILED, history records are automatically created.
     */
    DeliveryAssignmentResponse updateStatus(Long id, DeliveryStatus newStatus);

    /**
     * Cancels the current assignment for the given assignment ID and immediately
     * runs the auto-assignment algorithm again to find a replacement partner.
     *
     * Use cases: partner no-show, failed delivery needing a second attempt, manual admin override.
     *
     * The old assignment is moved to CANCELLED (which triggers history creation and
     * restores the original partner's weight/availability). Then assign() is called
     * on the same order to produce a fresh DeliveryAssignment.
     *
     * @param assignmentId the ID of the assignment to cancel and replace
     * @param reason       optional human-readable reason shown in the response
     * @return a ReassignResponse containing the old assignment ID and the new assignment
     */
    ReassignResponse reassign(Long assignmentId, String reason);
}
