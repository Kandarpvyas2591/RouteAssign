package com.routeassign.service.algorithm;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.domain.enums.ScoringMethod;

/**
 * Immutable value object that carries the validated scoring configuration for
 * one factor from the database to the scoring engine.
 *
 * The scoring engine receives a {@code ScoringRuleConfig} per enabled factor —
 * it never touches the repository or the service directly.
 *
 * <pre>
 * AssignmentScoringEngineImpl
 *        ↓  receives List<ScoringRuleConfig>
 *        ↓  populated by
 * AssignmentScoringRuleService
 *        ↓
 * AssignmentScoringRuleRepository
 *        ↓
 * MySQL  assignment_scoring_rules
 * </pre>
 *
 * @param factor        which scoring dimension this config describes
 * @param weight        relative weight; already validated (>= 0)
 * @param scoringMethod algorithm to apply to the raw measurement
 * @param minValue      lower normalisation bound (null for BINARY / NORMALIZED)
 * @param maxValue      upper normalisation bound (null for BINARY)
 * @param positiveScore BINARY only — score when condition is TRUE  (e.g. 100.0)
 * @param negativeScore BINARY only — score when condition is FALSE (e.g.   0.0)
 */
public record ScoringRuleConfig(
        AssignmentScoreFactor factor,
        double                weight,
        ScoringMethod         scoringMethod,
        Double                minValue,
        Double                maxValue,
        Double                positiveScore,
        Double                negativeScore
) {}
