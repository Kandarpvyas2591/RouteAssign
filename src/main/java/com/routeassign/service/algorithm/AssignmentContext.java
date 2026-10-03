package com.routeassign.service.algorithm;

import com.routeassign.domain.entity.Order;
import com.routeassign.domain.enums.AssignmentFailureReason;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Carries all context needed for one complete assignment or reassignment attempt.
 *
 * The same scoring engine and eligibility rules run for both initial assignments
 * and reassignments — the only difference is the context they receive:
 *
 * <pre>
 * Initial assignment:
 *   reason              = INITIAL_ASSIGNMENT (null)
 *   excludedPartnerIds  = []
 *   attemptNumber       = 1
 *
 * First reassignment (partner rejected):
 *   reason              = PARTNER_REJECTED
 *   excludedPartnerIds  = [25]
 *   attemptNumber       = 2
 *
 * Second reassignment (timeout):
 *   reason              = PARTNER_TIMEOUT
 *   excludedPartnerIds  = [25, 31]
 *   attemptNumber       = 3
 * </pre>
 *
 * Using {@code AssignmentContext} as a parameter instead of separate method
 * arguments makes {@link PartnerSelectionAlgorithmService#selectPartner(AssignmentContext)}
 * extensible — adding new context fields (e.g. priority tier, SLA deadline) does
 * not require changing every call site.
 */
public class AssignmentContext {

    private final Order                  order;
    private final Set<Long>              excludedPartnerIds;
    private final int                    attemptNumber;
    private final AssignmentFailureReason triggerReason;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Creates an initial-assignment context (attempt 1, no exclusions).
     *
     * @param order the order to be assigned
     */
    public AssignmentContext(Order order) {
        this(order, Collections.emptySet(), 1, null);
    }

    /**
     * Creates a reassignment context.
     *
     * @param order              the order to be reassigned
     * @param excludedPartnerIds partner IDs that must not be considered
     *                           (previous failed partners for this order)
     * @param attemptNumber      1-based attempt counter
     * @param triggerReason      why the previous attempt failed
     */
    public AssignmentContext(Order                   order,
                             Set<Long>               excludedPartnerIds,
                             int                     attemptNumber,
                             AssignmentFailureReason triggerReason) {
        this.order              = order;
        this.excludedPartnerIds = Collections.unmodifiableSet(new HashSet<>(excludedPartnerIds));
        this.attemptNumber      = attemptNumber;
        this.triggerReason      = triggerReason;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** The order being assigned. */
    public Order getOrder() { return order; }

    /**
     * Partner IDs that must be excluded from eligibility consideration.
     * Unmodifiable view — safe to share across threads.
     */
    public Set<Long> getExcludedPartnerIds() { return excludedPartnerIds; }

    /** 1-based attempt number. 1 = initial assignment, 2+ = reassignment. */
    public int getAttemptNumber() { return attemptNumber; }

    /**
     * Why the previous attempt failed, or {@code null} for the first attempt.
     * Used for logging and the audit trail.
     */
    public AssignmentFailureReason getTriggerReason() { return triggerReason; }

    /** Returns true if this is a reassignment (attempt > 1). */
    public boolean isReassignment() { return attemptNumber > 1; }

    /** Returns true if the given partner is on the exclusion list. */
    public boolean isExcluded(Long partnerId) {
        return excludedPartnerIds.contains(partnerId);
    }

    // ── Builder helper ────────────────────────────────────────────────────────

    /**
     * Returns a new context for the next attempt, adding {@code newlyFailedPartnerId}
     * to the exclusion list.
     */
    public AssignmentContext nextAttempt(Long newlyFailedPartnerId,
                                         AssignmentFailureReason reason) {
        Set<Long> newExclusions = new HashSet<>(excludedPartnerIds);
        newExclusions.add(newlyFailedPartnerId);
        return new AssignmentContext(order, newExclusions, attemptNumber + 1, reason);
    }

    @Override
    public String toString() {
        return "AssignmentContext{orderId=%d, attempt=%d, excluded=%s, reason=%s}"
                .formatted(order.getOrderId(), attemptNumber, excludedPartnerIds, triggerReason);
    }
}
