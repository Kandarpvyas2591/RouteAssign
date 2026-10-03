package com.routeassign.domain.enums;

/**
 * Codes that identify why a delivery partner was considered ineligible for
 * a specific order during the eligibility check phase of partner selection.
 *
 * Stored on {@code AssignmentDecisionCandidate.rejectionReason} so that the
 * system can answer "why was Partner X not chosen?" for any past order.
 *
 * Eligibility failures are absolute — no score can compensate for them.
 * A partner that fails any check is excluded before the scoring engine runs.
 */
public enum RejectionReason {

    /**
     * The partner's account is not active (isActive = false).
     */
    PARTNER_INACTIVE,

    /**
     * The partner is already on another delivery and cross-vendor busy-partner
     * reuse is disabled in the current configuration.
     */
    PARTNER_UNAVAILABLE,

    /**
     * The partner's remaining capacity (capacity − currentAssignedWeight) is
     * less than the order's total weight.
     */
    INSUFFICIENT_CAPACITY,

    /**
     * The assignment would start after the late-assignment cutoff hour AND the
     * partner's home-to-vendor distance exceeds the configured threshold, so
     * work would be deferred to the next working day — treated as ineligible
     * for immediate assignment.
     */
    OUTSIDE_WORKING_CONSTRAINT,

    /**
     * The partner is too far from the vendor for the same-vendor reuse check.
     * Only applied when evaluating same-vendor candidates; partners that fail
     * this check fall back to the general eligible pool rather than being
     * completely rejected.
     */
    SAME_VENDOR_DISTANCE_EXCEEDED,

    /**
     * The partner has no location data (latitude/longitude is null), making
     * distance calculation impossible.
     */
    MISSING_LOCATION_DATA,

    /**
     * Catch-all for any eligibility failure that does not map to a more
     * specific reason code.
     */
    OTHER
}
