package com.routeassign.domain.enums;

/**
 * Full lifecycle state machine for a {@code DeliveryAssignment}.
 *
 * Valid transitions:
 * <pre>
 *  ASSIGNED ──────────────────────────────────────────┐
 *      │                                              │
 *      ├─── ACCEPTED ──────────────────────────┐     │
 *      │         │                             │     │
 *      │         ├─── PICKED_UP               │     │
 *      │         │          │                 │     │
 *      │         │          └─── IN_TRANSIT   │     │
 *      │         │                    │       │     │
 *      │         │                    ├── DELIVERED  │
 *      │         │                    └── DELIVERY_FAILED → REASSIGNING
 *      │         └─── CANCELLED               │
 *      │                                      │
 *      ├─── REJECTED → REASSIGNING            │
 *      ├─── EXPIRED  → REASSIGNING            │
 *      └─── CANCELLED                         │
 *                                             │
 *  REASSIGNING ──────────────────────────────►│
 *      │
 *      ├─── (new ASSIGNED attempt created)
 *      └─── WAITING_FOR_PARTNER  (no eligible partner found)
 * </pre>
 *
 * Terminal statuses (no further transitions): DELIVERED, CANCELLED, WAITING_FOR_PARTNER.
 * Historical-only (kept for audit, not reassigned): REJECTED, EXPIRED, DELIVERY_FAILED.
 */
public enum DeliveryStatus {

    // ── Active states ─────────────────────────────────────────────────────────

    /** Assignment created; waiting for partner to accept. */
    ASSIGNED,

    /** Partner has accepted the assignment; travelling to vendor. */
    ACCEPTED,

    /** Partner has arrived at vendor and collected the order. */
    PICKED_UP,

    /** Partner is travelling to the customer. */
    IN_TRANSIT,

    // ── Terminal — success ────────────────────────────────────────────────────

    /** Delivery successfully completed. */
    DELIVERED,

    // ── Terminal — failure / cancellation ────────────────────────────────────

    /** Assignment was cancelled (by customer, admin, or system). */
    CANCELLED,

    /** Delivery failed after pickup (e.g. customer unreachable, accident). */
    DELIVERY_FAILED,

    // ── Rejection / timeout ───────────────────────────────────────────────────

    /** Partner explicitly rejected the assignment. Triggers reassignment. */
    REJECTED,

    /**
     * Partner did not accept within ASSIGNMENT_ACCEPTANCE_TIMEOUT_MINUTES.
     * Detected by {@code AssignmentExpiryService}. Triggers reassignment.
     */
    EXPIRED,

    // ── Reassignment states ───────────────────────────────────────────────────

    /**
     * This attempt has ended and a new assignment attempt is being created.
     * Transient — the system moves through this state automatically.
     */
    REASSIGNING,

    /**
     * Reassignment was attempted but no eligible partner could be found.
     * The order waits until a partner becomes available or until manually handled.
     * Terminal for this attempt; a future retry can be triggered externally.
     */
    WAITING_FOR_PARTNER,

    // ── Legacy (kept for backward compatibility with existing data) ───────────

    /** @deprecated Use {@link #PICKED_UP} — kept for existing history records. */
    @Deprecated
    EN_ROUTE_TO_VENDOR,

    /** @deprecated Use {@link #IN_TRANSIT} — kept for existing history records. */
    @Deprecated
    COLLECTED,

    /** @deprecated Use {@link #DELIVERY_FAILED} — kept for existing history records. */
    @Deprecated
    FAILED
}
