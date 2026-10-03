package com.routeassign.service;

import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.domain.enums.AssignmentFailureReason;
import com.routeassign.dto.response.DeliveryAssignmentResponse;

/**
 * Orchestrates assignment reassignment using the same eligibility and scoring
 * engine as the initial assignment.
 *
 * <b>Key invariants</b>
 * <ul>
 *   <li>Reassignment creates a NEW {@code DeliveryAssignment} row — the failed
 *       attempt is never overwritten, preserving the full history.</li>
 *   <li>The failed partner is excluded from the new attempt via
 *       {@link com.routeassign.service.algorithm.AssignmentContext}.</li>
 *   <li>If {@code MAX_ASSIGNMENT_ATTEMPTS} is exceeded, the assignment moves to
 *       {@code WAITING_FOR_PARTNER} instead of retrying.</li>
 *   <li>If no eligible partner exists, the order moves to
 *       {@code WAITING_FOR_PARTNER} without an infinite retry loop.</li>
 * </ul>
 *
 * This service does not contain its own selection logic — it builds an
 * {@code AssignmentContext} and delegates to
 * {@link com.routeassign.service.algorithm.PartnerSelectionAlgorithmService}.
 */
public interface AssignmentReassignmentService {

    /**
     * Triggers a reassignment for the given failed assignment.
     *
     * <pre>
     * 1. Close the failed assignment (mark as REASSIGNING, record failureReason).
     * 2. Collect all previously failed partner IDs for this order.
     * 3. Build AssignmentContext with exclusions and next attempt number.
     * 4. Check attempt count against MAX_ASSIGNMENT_ATTEMPTS.
     *    If exceeded → set order to WAITING_FOR_PARTNER, return null.
     * 5. Run eligibility + scoring via PartnerSelectionAlgorithmService.
     * 6. If partner found → persist new DeliveryAssignment (attempt N+1).
     * 7. If no partner → set order to WAITING_FOR_PARTNER, return null.
     * </pre>
     *
     * @param failedAssignment the assignment that ended in a non-delivery status
     * @param failureReason    why the previous attempt failed
     * @return the new assignment response, or {@code null} if the order
     *         moved to WAITING_FOR_PARTNER
     */
    DeliveryAssignmentResponse reassign(DeliveryAssignment failedAssignment,
                                        AssignmentFailureReason failureReason);
}
