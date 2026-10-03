package com.routeassign.exception;

/**
 * Thrown internally when the top-scored candidate partner fails the
 * post-lock eligibility re-check.
 *
 * This exception is caught inside the assignment loop so the service can
 * fall back to the next-best scored candidate rather than surfacing a 5xx
 * error to the caller.
 *
 * Flow:
 * <pre>
 * Lock partner A
 *   ↓
 * Re-read fresh state from DB
 *   ↓
 * Re-check eligibility
 *   ↓ (fails — e.g. another request consumed the capacity)
 * throw AssignmentAttemptException
 *   ↓
 * Catch in assign() loop
 *   ↓
 * Try next candidate (Partner B)
 * </pre>
 *
 * If all candidates fail re-check, {@link NoEligiblePartnerException} is thrown.
 */
public class AssignmentAttemptException extends RuntimeException {

    private final Long partnerId;
    private final String rejectReason;

    public AssignmentAttemptException(Long partnerId, String rejectReason) {
        super("Partner " + partnerId + " failed post-lock re-check: " + rejectReason);
        this.partnerId    = partnerId;
        this.rejectReason = rejectReason;
    }

    public Long getPartnerId()    { return partnerId; }
    public String getRejectReason() { return rejectReason; }
}
