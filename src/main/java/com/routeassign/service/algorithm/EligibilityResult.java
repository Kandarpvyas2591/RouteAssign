package com.routeassign.service.algorithm;

import com.routeassign.domain.entity.UserDetails;
import com.routeassign.domain.enums.RejectionReason;

/**
 * Immutable result of the eligibility check for one delivery partner candidate.
 *
 * Separates the question <em>"Can this partner receive this order?"</em> from
 * the scoring question <em>"Among eligible partners, how suitable is this one?"</em>
 *
 * A partner that fails eligibility is NEVER scored — no composite score can
 * compensate for an eligibility failure.
 *
 * <pre>
 * Example — eligible:
 *   EligibilityResult.eligible(partner)
 *   → eligible=true, rejectionReason=null
 *
 * Example — ineligible:
 *   EligibilityResult.rejected(partner, RejectionReason.INSUFFICIENT_CAPACITY)
 *   → eligible=false, rejectionReason=INSUFFICIENT_CAPACITY
 * </pre>
 *
 * @param partner         the evaluated delivery partner
 * @param eligible        true if the partner may proceed to scoring
 * @param rejectionReason why the partner was rejected; null when eligible=true
 */
public record EligibilityResult(
        UserDetails    partner,
        boolean        eligible,
        RejectionReason rejectionReason
) {

    // ── Factory methods ───────────────────────────────────────────────────────

    /** Creates an eligible result — no rejection reason. */
    public static EligibilityResult eligible(UserDetails partner) {
        return new EligibilityResult(partner, true, null);
    }

    /**
     * Creates a rejected result with an explicit reason.
     *
     * @param partner the candidate that failed eligibility
     * @param reason  the specific reason for rejection
     */
    public static EligibilityResult rejected(UserDetails partner, RejectionReason reason) {
        return new EligibilityResult(partner, false, reason);
    }

    // ── Convenience accessors ─────────────────────────────────────────────────

    /** Returns true if this result represents a rejected (ineligible) partner. */
    public boolean isRejected() {
        return !eligible;
    }

    /** Returns the partner's ID — shortcut to avoid calling partner().getId() everywhere. */
    public Long partnerId() {
        return partner.getId();
    }
}
