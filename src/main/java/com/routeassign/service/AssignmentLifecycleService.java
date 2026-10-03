package com.routeassign.service;

import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.dto.response.DeliveryAssignmentResponse;

/**
 * Controls the lifecycle state machine for a {@link DeliveryAssignment}.
 *
 * Every status transition in the system must go through this service.
 * Controllers and other services must NOT set {@code deliveryStatus} directly —
 * they call the appropriate lifecycle method here, which validates the transition
 * and triggers any side effects (history creation, weight adjustment, reassignment).
 *
 * Valid transition graph:
 * <pre>
 *  ASSIGNED ──────────┬──────────┬─────────────┐
 *      │              │          │             │
 *   ACCEPTED       REJECTED   EXPIRED      CANCELLED
 *      │              └────┬────┘
 *   PICKED_UP             REASSIGNING → (new ASSIGNED attempt)
 *      │                   └─── WAITING_FOR_PARTNER (if no partner found)
 *   IN_TRANSIT
 *      ├── DELIVERED
 *      └── DELIVERY_FAILED → REASSIGNING
 * </pre>
 */
public interface AssignmentLifecycleService {

    /**
     * Partner accepted the assignment (ASSIGNED → ACCEPTED).
     */
    DeliveryAssignmentResponse acceptAssignment(Long assignmentId);

    /**
     * Partner explicitly rejected the assignment (ASSIGNED → REJECTED).
     * Triggers reassignment via {@link AssignmentReassignmentService}.
     *
     * @param assignmentId the assignment being rejected
     * @param reason       optional free-text reason for the rejection
     */
    DeliveryAssignmentResponse rejectAssignment(Long assignmentId, String reason);

    /**
     * System marks the assignment as expired because the partner did not accept
     * within the configured timeout (ASSIGNED → EXPIRED).
     * Triggers reassignment via {@link AssignmentReassignmentService}.
     * Called by {@link AssignmentExpiryService}.
     */
    DeliveryAssignmentResponse expireAssignment(Long assignmentId);

    /**
     * Assignment cancelled by customer, admin, or system
     * (ASSIGNED | ACCEPTED → CANCELLED).
     * Does NOT trigger reassignment — caller is responsible for that if needed.
     *
     * @param assignmentId the assignment to cancel
     * @param reason       optional cancellation reason
     */
    DeliveryAssignmentResponse cancelAssignment(Long assignmentId, String reason);

    /**
     * Partner has collected the order from the vendor (ACCEPTED → PICKED_UP).
     */
    DeliveryAssignmentResponse markPickedUp(Long assignmentId);

    /**
     * Partner is now travelling to the customer (PICKED_UP → IN_TRANSIT).
     */
    DeliveryAssignmentResponse markInTransit(Long assignmentId);

    /**
     * Delivery successfully completed (IN_TRANSIT → DELIVERED).
     * Records history, restores partner availability, reduces assigned weight.
     */
    DeliveryAssignmentResponse completeDelivery(Long assignmentId);

    /**
     * Delivery failed after pickup (IN_TRANSIT → DELIVERY_FAILED).
     * Triggers reassignment via {@link AssignmentReassignmentService}.
     *
     * @param assignmentId the assignment that failed
     * @param reason       optional free-text reason for the failure
     */
    DeliveryAssignmentResponse markDeliveryFailed(Long assignmentId, String reason);
}
