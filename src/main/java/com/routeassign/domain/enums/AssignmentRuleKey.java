package com.routeassign.domain.enums;

/**
 * Canonical keys for every business rule stored in the {@code assignment_rules} table.
 *
 * Adding a new rule requires:
 *  1. A new constant here.
 *  2. A seed record in {@code DataInitializer}.
 *  3. A validation entry in {@code AssignmentRuleServiceImpl.validate(...)}.
 */
public enum AssignmentRuleKey {

    /**
     * Maximum extra distance (km) a same-vendor delivery partner may travel to
     * the new customer location before the system stops reusing that partner.
     * Type: DOUBLE — must be > 0.
     */
    SAME_VENDOR_MAX_DISTANCE,

    /**
     * If the assignment is placed after {@link #LATE_ASSIGNMENT_HOUR} AND the
     * home-to-vendor distance exceeds this threshold (km), work is deferred to
     * the next working day.
     * Type: DOUBLE — must be > 0.
     */
    HOME_VENDOR_MAX_DISTANCE,

    /**
     * Working day start hour (24-h clock, inclusive).
     * E.g. 10 means 10:00 AM.
     * Type: INTEGER — must be in [0, 23] and < WORKING_HOUR_END.
     */
    WORKING_HOUR_START,

    /**
     * Working day end hour (24-h clock, exclusive).
     * E.g. 20 means 8:00 PM.
     * Type: INTEGER — must be in [1, 24] and > WORKING_HOUR_START.
     */
    WORKING_HOUR_END,

    /**
     * Hour after which the home-to-vendor distance check is applied (24-h clock).
     * E.g. 17 means 5:00 PM.
     * Type: INTEGER — must be in [0, 23].
     */
    LATE_ASSIGNMENT_HOUR,

    /**
     * Minutes a delivery partner rests at home between completing one delivery
     * and starting the next one (used in the busy-partner ETA formula).
     * Type: INTEGER — must be >= 0.
     */
    REST_DURATION_MINUTES,

    /**
     * Flag controlling whether the assignment engine considers busy partners
     * who are currently mid-delivery on a different vendor's order.
     * Type: BOOLEAN — "true" or "false".
     */
    ENABLE_BUSY_PARTNER_REUSE,

    /**
     * Minutes a partner has to accept an assignment before it expires and
     * triggers automatic reassignment.
     * Type: INTEGER — must be > 0.
     * Used by {@code AssignmentExpiryService}.
     */
    ASSIGNMENT_ACCEPTANCE_TIMEOUT_MINUTES,

    /**
     * Maximum number of assignment attempts before an order is escalated to
     * WAITING_FOR_PARTNER and requires manual intervention.
     * Type: INTEGER — must be >= 1.
     * Used by {@code AssignmentReassignmentService}.
     */
    MAX_ASSIGNMENT_ATTEMPTS
}
