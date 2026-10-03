package com.routeassign.service;

import com.routeassign.domain.entity.AssignmentScoringRule;
import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.domain.enums.ScoringMethod;
import com.routeassign.service.algorithm.ScoringRuleConfig;

import java.util.List;

/**
 * Central access point for scoring configuration stored in
 * {@code assignment_scoring_rules}.
 *
 * Scoring strategies must call this interface — never the repository directly.
 *
 * <pre>
 * AssignmentScoringEngineImpl
 *        ↓
 * AssignmentScoringRuleService   ← validation, error handling
 *        ↓
 * AssignmentScoringRuleRepository
 *        ↓
 * MySQL  assignment_scoring_rules
 * </pre>
 */
public interface AssignmentScoringRuleService {

    // ── Scoring engine access ─────────────────────────────────────────────────

    /**
     * Returns a validated {@link ScoringRuleConfig} for every currently enabled
     * factor.  The list is ordered by factor declaration order for determinism.
     *
     * Validates:
     * <ul>
     *   <li>All returned configs have weight >= 0.</li>
     *   <li>No duplicate factors in the enabled set.</li>
     *   <li>At least one enabled factor exists (otherwise scoring is meaningless).</li>
     *   <li>Total enabled weight > 0 (guards against all-zero weights).</li>
     * </ul>
     *
     * @throws com.routeassign.exception.InvalidRuleException if the active
     *         configuration fails any validation check.
     */
    List<ScoringRuleConfig> getEnabledScoringConfigs();

    // ── Admin read access ─────────────────────────────────────────────────────

    /** Returns all scoring rules (enabled and disabled) for the admin listing endpoint. */
    List<AssignmentScoringRule> getAllRules();

    /**
     * Returns a single scoring rule by factor, regardless of enabled status.
     *
     * @throws com.routeassign.exception.ResourceNotFoundException if the factor
     *         has no row in the database.
     */
    AssignmentScoringRule getRuleByFactor(AssignmentScoreFactor factor);

    // ── Admin write access ────────────────────────────────────────────────────

    /**
     * Updates a scoring rule's configurable fields and records who made the change.
     *
     * Validates the individual field values and the resulting system-wide state:
     * <ul>
     *   <li>weight >= 0</li>
     *   <li>scoringMethod is a valid enum value</li>
     *   <li>minValue &lt; maxValue when both are provided for LINEAR / INVERSE_LINEAR</li>
     *   <li>non-BINARY factors must have maxValue &gt; minValue</li>
     *   <li>BINARY factors must have positiveScore >= negativeScore, both in [0, 100]</li>
     *   <li>After applying the change, total enabled weight must remain &gt; 0</li>
     * </ul>
     *
     * @param factor        the factor to update
     * @param weight        new relative weight (null = keep existing)
     * @param isEnabled     new enabled flag (null = keep existing)
     * @param scoringMethod new scoring method (null = keep existing)
     * @param minValue      new lower normalisation bound (null = keep existing)
     * @param maxValue      new upper normalisation bound (null = keep existing)
     * @param positiveScore BINARY only — score when condition is TRUE  (null = keep existing)
     * @param negativeScore BINARY only — score when condition is FALSE (null = keep existing)
     * @param updatedBy     identifier of the admin performing the change
     * @return the saved {@link AssignmentScoringRule}
     * @throws com.routeassign.exception.ResourceNotFoundException if the factor
     *         does not exist.
     * @throws com.routeassign.exception.InvalidRuleException if validation fails.
     */
    AssignmentScoringRule updateRule(AssignmentScoreFactor factor,
                                     Double        weight,
                                     Boolean       isEnabled,
                                     ScoringMethod scoringMethod,
                                     Double        minValue,
                                     Double        maxValue,
                                     Double        positiveScore,
                                     Double        negativeScore,
                                     String        updatedBy);
}
