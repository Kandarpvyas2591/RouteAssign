package com.routeassign.service.algorithm;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.dto.response.ScoringResult;
import com.routeassign.service.algorithm.strategy.CandidateContext;

import java.util.Map;

/**
 * Orchestrates the weighted composite scoring of a single eligible candidate.
 *
 * The engine is the only caller of the five {@code AssignmentScoringStrategy}
 * implementations.  It does not know the internal calculation of any factor —
 * it only discovers strategies, passes them the context and DB-configured
 * parameters, collects per-factor scores, and computes the weighted final score.
 *
 * <pre>
 * AssignmentScoringEngineImpl
 *   ├─ List&lt;AssignmentScoringStrategy&gt;  (injected by Spring — one per factor)
 *   └─ AssignmentScoringRuleService     (reads enabled configs from DB)
 *
 * score(AssignmentCandidate)
 *   ↓  candidate.toContext()
 *   ↓  for each enabled ScoringRuleConfig
 *   ↓  finds matching strategy by factor
 *   ↓  strategy.calculateScore(context, config) → factorScore [0,100]
 *   ↓  finalScore = weightedSum / totalWeight
 *   ↓  returns ScoringResult with all per-factor scores + finalScore
 * </pre>
 *
 * Returns {@link ScoringResult}, not a plain {@code double}, so callers can
 * persist the full breakdown as an {@code AssignmentDecisionCandidate} record.
 */
public interface AssignmentScoringEngine {

    /**
     * Scores an eligible candidate using the DB-configured weights and strategies.
     *
     * This is the primary entry point used by the orchestrator.
     * {@link AssignmentCandidate#toContext()} is called internally to produce
     * the {@link CandidateContext} passed to each strategy.
     *
     * @param candidate the fully built eligible candidate
     * @return full {@link ScoringResult} with individual factor scores (0–100 each)
     *         and the weighted {@link ScoringResult#getFinalScore()}
     * @throws com.routeassign.exception.InvalidRuleException if no enabled scoring
     *         factors exist in the DB
     */
    ScoringResult score(AssignmentCandidate candidate);

    /**
     * Low-level overload — scores using a pre-built {@link CandidateContext}.
     * Kept for backward-compatible access; prefer {@link #score(AssignmentCandidate)}.
     *
     * @param context pre-computed candidate measurements
     * @return full {@link ScoringResult}
     */
    ScoringResult score(CandidateContext context);

    /**
     * Returns a snapshot of the factor → weight mapping that is currently active.
     *
     * Called by the orchestrator immediately before scoring to capture the weights
     * into the {@code AssignmentDecision} audit record, so later admin changes to
     * weights do not obscure historical reasoning.
     *
     * @return unmodifiable map of enabled factor → its configured weight
     */
    Map<AssignmentScoreFactor, Double> getEnabledWeights();
}
