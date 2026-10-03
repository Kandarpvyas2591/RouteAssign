package com.routeassign.service.algorithm.strategy;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.service.algorithm.ScoringRuleConfig;

/**
 * Strategy interface for assignment scoring.
 *
 * Each implementation is responsible for exactly one {@link AssignmentScoreFactor}.
 * The engine does not know the internal calculation of any factor — it only knows
 * how to discover strategies, pass them the context and config, and collect scores.
 *
 * <pre>
 * AssignmentScoringEngineImpl
 *        │  injects List<AssignmentScoringStrategy> (all implementations)
 *        │  maps each ScoringRuleConfig.factor() → matching strategy
 *        ↓
 * AssignmentScoringStrategy.calculateScore(context, config)
 *        ↓  returns a score in [0.0, 100.0]
 * AssignmentScoringEngineImpl accumulates weighted sum
 * </pre>
 *
 * <b>Adding a new factor</b> requires:
 * <ol>
 *   <li>A new constant in {@link AssignmentScoreFactor}.</li>
 *   <li>A new {@code @Component} class implementing this interface.</li>
 *   <li>A seed row in {@code DataInitializer}.</li>
 *   <li>Validation in {@code AssignmentScoringRuleServiceImpl.validateFields()}.</li>
 * </ol>
 * No modification of the engine or any existing strategy is required.
 */
public interface AssignmentScoringStrategy {

    /**
     * Returns the scoring factor this strategy handles.
     * Used by the engine to match strategies to {@link ScoringRuleConfig} entries.
     */
    AssignmentScoreFactor getFactor();

    /**
     * Calculates a normalised score for the candidate described by {@code context},
     * using the DB-configured parameters in {@code config}.
     *
     * <ul>
     *   <li>Return value is on a <b>0–100 scale</b>.</li>
     *   <li>Higher score = better fit for this factor.</li>
     *   <li>The strategy must clamp its output to [0.0, 100.0].</li>
     *   <li>The strategy must not access repositories or other services
     *       directly — all inputs arrive via {@code context} and {@code config}.</li>
     * </ul>
     *
     * @param context pre-computed candidate measurements for the order
     * @param config  validated DB configuration for this factor
     *                (weight, scoringMethod, minValue, maxValue)
     * @return score in [0.0, 100.0]; higher is better
     */
    double calculateScore(CandidateContext context, ScoringRuleConfig config);
}
