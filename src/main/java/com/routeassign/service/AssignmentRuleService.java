package com.routeassign.service;

import com.routeassign.domain.entity.AssignmentRule;
import com.routeassign.domain.enums.AssignmentRuleKey;

import java.util.List;

/**
 * Central access point for all assignment business rules stored in the DB.
 *
 * Algorithm services must call this interface — never the repository directly.
 * This keeps validation, error-handling, and future caching isolated here.
 *
 * <pre>
 * Algorithm service
 *       ↓
 * AssignmentRuleService   ← typed getters, validation
 *       ↓
 * AssignmentRuleRepository
 *       ↓
 * MySQL  assignment_rules
 * </pre>
 */
public interface AssignmentRuleService {

    // ── Typed read access ─────────────────────────────────────────────────────

    /**
     * Returns the current value of a DOUBLE rule.
     *
     * @throws com.routeassign.exception.ResourceNotFoundException if the rule key
     *         does not exist in the DB or is inactive.
     * @throws com.routeassign.exception.InvalidRuleException if the stored value
     *         cannot be parsed as a double or fails domain validation.
     */
    double getDoubleRule(AssignmentRuleKey key);

    /**
     * Returns the current value of an INTEGER rule.
     *
     * @throws com.routeassign.exception.ResourceNotFoundException if the rule key
     *         does not exist in the DB or is inactive.
     * @throws com.routeassign.exception.InvalidRuleException if the stored value
     *         cannot be parsed as an integer or fails domain validation.
     */
    int getIntegerRule(AssignmentRuleKey key);

    /**
     * Returns the current value of a BOOLEAN rule.
     *
     * @throws com.routeassign.exception.ResourceNotFoundException if the rule key
     *         does not exist in the DB or is inactive.
     * @throws com.routeassign.exception.InvalidRuleException if the stored value
     *         is not a recognised boolean string ("true" / "false").
     */
    boolean getBooleanRule(AssignmentRuleKey key);

    // ── Admin read access ─────────────────────────────────────────────────────

    /** Returns all active {@link AssignmentRule} entities (for the admin listing endpoint). */
    List<AssignmentRule> getAllActiveRules();

    /**
     * Returns a single {@link AssignmentRule} by key regardless of active status
     * (needed so the admin can inspect and re-activate a deactivated rule).
     *
     * @throws com.routeassign.exception.ResourceNotFoundException if no row exists.
     */
    AssignmentRule getRuleByKey(AssignmentRuleKey key);

    // ── Admin write access ────────────────────────────────────────────────────

    /**
     * Updates the value of an existing rule and records who made the change.
     *
     * <ul>
     *   <li>Validates that the new value is parseable for the rule's declared type.</li>
     *   <li>Validates domain constraints (e.g. hours in [0, 23]).</li>
     *   <li>Validates cross-rule constraints (e.g. WORKING_HOUR_START &lt; WORKING_HOUR_END).</li>
     * </ul>
     *
     * @param key       the rule to update
     * @param newValue  the new raw string value
     * @param updatedBy identifier of the admin performing the change (for audit)
     * @return the saved {@link AssignmentRule}
     * @throws com.routeassign.exception.ResourceNotFoundException if the rule does not exist.
     * @throws com.routeassign.exception.InvalidRuleException if the value fails any validation.
     */
    AssignmentRule updateRule(AssignmentRuleKey key, String newValue, String updatedBy);
}
