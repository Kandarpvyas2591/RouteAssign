package com.routeassign.domain.enums;

/**
 * Identifies each scoring dimension used by the assignment engine's weighted
 * composite scorer.
 *
 * Each factor has a corresponding row in the {@code assignment_scoring_rules} table
 * that stores its weight, enabled flag, scoring method, and min/max normalization
 * bounds.  The algorithm reads those values at runtime — no factor weight is
 * hardcoded in Java.
 *
 * Adding a new factor requires:
 *  1. A new constant here.
 *  2. A seed row in {@code DataInitializer}.
 *  3. Scoring logic in {@code AssignmentScoringEngineImpl} and a new {@code AssignmentScoringStrategy} implementation.
 *  4. A validation branch in {@code AssignmentScoringRuleServiceImpl}.
 */
public enum AssignmentScoreFactor {

    /**
     * Total route distance (partner → vendor → customer) in km.
     * Lower distance is better.
     * Default scoring method: INVERSE_LINEAR.
     */
    DISTANCE,

    /**
     * Remaining carrying capacity of the partner (capacity − currentAssignedWeight).
     * Higher remaining capacity is better.
     * Default scoring method: LINEAR.
     */
    CAPACITY,

    /**
     * Aggregate partner rating (0.0 – 5.0).
     * Higher rating is better.
     * Default scoring method: NORMALIZED.
     */
    RATING,

    /**
     * Time elapsed since the partner last completed a delivery (minutes).
     * Longer idle time is better (fairness — reward less-busy partners).
     * Default scoring method: LINEAR.
     */
    IDLE_TIME,

    /**
     * Whether the partner is already assigned to the same vendor for the current order.
     * Binary — same-vendor partner scores 1.0, otherwise 0.0.
     * Default scoring method: BINARY.
     */
    SAME_VENDOR
}
