package com.routeassign.service;

/**
 * Detects and processes assignments that have exceeded the partner acceptance
 * timeout configured in {@code ASSIGNMENT_ACCEPTANCE_TIMEOUT_MINUTES}.
 *
 * <b>How it works</b>
 * <pre>
 * Scheduler calls detectAndExpire() periodically.
 *       ↓
 * Queries DB for: status=ASSIGNED AND assigned_at + timeout <= now
 *       ↓
 * For each expired assignment:
 *   AssignmentLifecycleService.expireAssignment(id)
 *       ↓
 *   ASSIGNED → EXPIRED → REASSIGNING → new attempt
 * </pre>
 *
 * <b>Design notes</b>
 * <ul>
 *   <li>This service only <em>detects</em> expiry. The actual status transitions
 *       and reassignment logic live in {@link AssignmentLifecycleService} and
 *       {@link AssignmentReassignmentService} respectively.</li>
 *   <li>A scheduled trigger (e.g. {@code @Scheduled}) on a Spring Boot component
 *       calls {@link #detectAndExpire()} at a configured interval.</li>
 *   <li>Redis distributed locking is not required in Phase 7 — the DB-level
 *       query and single-instance assumption make it unnecessary for now.</li>
 * </ul>
 */
public interface AssignmentExpiryService {

    /**
     * Scans for all ASSIGNED assignments whose acceptance window has passed and
     * expires them one by one.
     *
     * Each expiry triggers reassignment via {@link AssignmentLifecycleService}.
     * Safe to call repeatedly — already-expired assignments are not matched by
     * the query.
     *
     * @return the number of assignments that were expired in this run
     */
    int detectAndExpire();
}
