package com.routeassign.service;

import com.routeassign.domain.entity.Order;
import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.dto.response.ReassignResponse;
import com.routeassign.service.algorithm.AssignmentContext;

import java.util.List;

public interface DeliveryAssignmentService {

    /**
     * Entry point for the auto-assignment algorithm — initial assignment (attempt 1).
     * Wraps the order in a fresh {@link AssignmentContext} and delegates.
     *
     * @param order the newly placed order
     * @return the persisted DeliveryAssignmentResponse
     */
    DeliveryAssignmentResponse assign(Order order);

    /**
     * Context-aware assignment entry point used by reassignment logic.
     * Carries the attempt number, previously excluded partner IDs, and the
     * failure reason from the prior attempt.
     *
     * @param order   the order to assign
     * @param context the assignment context (attempt number, exclusions, reason)
     * @return the persisted DeliveryAssignmentResponse
     */
    DeliveryAssignmentResponse assign(Order order, AssignmentContext context);

    DeliveryAssignmentResponse getById(Long id);

    DeliveryAssignmentResponse getByOrderId(Long orderId);

    List<DeliveryAssignmentResponse> getByPartnerId(Long partnerId);

    List<DeliveryAssignmentResponse> getByVendorId(Long vendorId);

    List<DeliveryAssignmentResponse> getByStatus(DeliveryStatus status);

    /**
     * Updates the delivery status of an assignment.
     * Prefer {@link AssignmentLifecycleService} methods for business-driven
     * transitions; this method is retained for admin/operational overrides.
     */
    DeliveryAssignmentResponse updateStatus(Long id, DeliveryStatus newStatus);

    /**
     * Legacy manual-reassignment entry point (admin override).
     * Cancels the current assignment and immediately runs a fresh assignment
     * on the same order.  For system-driven reassignment (reject/expire/fail),
     * use {@link AssignmentReassignmentService} instead.
     */
    ReassignResponse reassign(Long assignmentId, String reason);
}
