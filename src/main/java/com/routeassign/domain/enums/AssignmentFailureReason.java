package com.routeassign.domain.enums;

/**
 * Describes why a specific delivery assignment attempt ended unsuccessfully.
 *
 * Stored on {@link com.routeassign.domain.entity.DeliveryAssignment#getFailureReason()}
 * so the system can answer "why did attempt #2 for Order #101 fail?" for any past order.
 *
 * Used by:
 * - {@code AssignmentLifecycleService} when recording a rejection or failure.
 * - {@code AssignmentExpiryService} when marking an assignment as EXPIRED.
 * - Analytics / admin dashboards to understand rejection patterns.
 */
public enum AssignmentFailureReason {

    // ── Partner-initiated ─────────────────────────────────────────────────────

    /** Partner explicitly rejected the assignment through the app. */
    PARTNER_REJECTED,

    /**
     * Partner did not accept within the configured timeout window
     * ({@code ASSIGNMENT_ACCEPTANCE_TIMEOUT_MINUTES}).
     */
    PARTNER_TIMEOUT,

    /** Partner cancelled after accepting (before or during pickup). */
    PARTNER_CANCELLED,

    /**
     * Partner became unavailable (account deactivated, connectivity lost, etc.)
     * after the assignment was created.
     */
    PARTNER_UNAVAILABLE,

    // ── Delivery-time failures ────────────────────────────────────────────────

    /**
     * Delivery failed after the order was picked up
     * (e.g. customer unreachable, address incorrect, accident).
     */
    DELIVERY_FAILED,

    // ── System-detected ───────────────────────────────────────────────────────

    /**
     * Partner's remaining capacity dropped below the order weight between
     * scoring and locking — detected during the post-lock re-check.
     */
    PARTNER_CAPACITY_CHANGED,

    /**
     * Reassignment was triggered but no eligible partner could be found.
     * The order transitions to WAITING_FOR_PARTNER.
     */
    NO_ELIGIBLE_PARTNER,

    /**
     * The maximum number of assignment attempts ({@code MAX_ASSIGNMENT_ATTEMPTS})
     * has been reached. Requires manual intervention.
     */
    MAX_ATTEMPTS_REACHED,

    /** Catch-all for unexpected system errors during assignment processing. */
    SYSTEM_ERROR
}
