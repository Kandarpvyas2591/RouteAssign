package com.routeassign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned by POST /api/assignments/{id}/reassign
 * Wraps the new DeliveryAssignment created after the old one is cancelled.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReassignResponse {

    /** The ID of the old assignment that was cancelled. */
    private Long oldAssignmentId;

    /** The full new assignment selected by the algorithm. */
    private DeliveryAssignmentResponse newAssignment;

    /** Human-readable reason describing why a reassignment was triggered. */
    private String reason;
}
