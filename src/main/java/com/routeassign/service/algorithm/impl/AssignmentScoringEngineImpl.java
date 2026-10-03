package com.routeassign.service.algorithm.impl;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.dto.response.ScoringResult;
import com.routeassign.exception.InvalidRuleException;
import com.routeassign.service.AssignmentScoringRuleService;
import com.routeassign.service.algorithm.AssignmentCandidate;
import com.routeassign.service.algorithm.AssignmentScoringEngine;
import com.routeassign.service.algorithm.ScoringRuleConfig;
import com.routeassign.service.algorithm.strategy.AssignmentScoringStrategy;
import com.routeassign.service.algorithm.strategy.CandidateContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link AssignmentScoringEngine}.
 *
 * <b>Dynamic strategy discovery</b>
 * Spring injects every {@code @Component} implementing {@link AssignmentScoringStrategy}
 * as a list.  At first use it is converted to a factor→strategy map for O(1) lookup.
 * No changes are required here when a new strategy is added.
 *
 * <b>Scoring flow</b>
 * <pre>
 * 1. score(AssignmentCandidate) → calls toContext() → delegates to score(CandidateContext).
 * 2. Load enabled ScoringRuleConfigs from DB via AssignmentScoringRuleService.
 * 3. For each config look up the matching strategy.
 * 4. Invoke strategy.calculateScore(context, config) → factorScore [0,100].
 * 5. Accumulate weightedSum += factorScore × weight; totalWeight += weight.
 * 6. finalScore = weightedSum / totalWeight  (stays on 0–100 scale).
 * 7. Build and return ScoringResult.
 * </pre>
 *
 * <b>Weight snapshot</b>
 * {@link #getEnabledWeights()} returns the live factor→weight map so the orchestrator
 * can capture a snapshot into {@code AssignmentDecision} before weights are changed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentScoringEngineImpl implements AssignmentScoringEngine {

    private final AssignmentScoringRuleService    assignmentScoringRuleService;
    private final List<AssignmentScoringStrategy> strategies;

    // ── Strategy map: built once, reused ─────────────────────────────────────

    private volatile Map<AssignmentScoreFactor, AssignmentScoringStrategy> strategyMap;

    private Map<AssignmentScoreFactor, AssignmentScoringStrategy> getStrategyMap() {
        if (strategyMap == null) {
            synchronized (this) {
                if (strategyMap == null) {
                    strategyMap = strategies.stream()
                            .collect(Collectors.toMap(
                                    AssignmentScoringStrategy::getFactor,
                                    Function.identity()
                            ));
                    log.info("AssignmentScoringEngine: registered {} strategies: {}",
                            strategyMap.size(), strategyMap.keySet());
                }
            }
        }
        return strategyMap;
    }

    // ── Primary entry point (AssignmentCandidate) ─────────────────────────────

    /**
     * Scores an eligible candidate by extracting its {@link CandidateContext}
     * and forwarding to the context-based overload.
     * Strategies are not affected — they still receive a {@link CandidateContext}.
     */
    @Override
    public ScoringResult score(AssignmentCandidate candidate) {
        return score(candidate.toContext());
    }

    // ── Low-level overload (CandidateContext) ─────────────────────────────────

    @Override
    public ScoringResult score(CandidateContext context) {
        List<ScoringRuleConfig> configs = assignmentScoringRuleService.getEnabledScoringConfigs();
        Map<AssignmentScoreFactor, AssignmentScoringStrategy> map = getStrategyMap();

        double distanceScore   = 0.0;
        double capacityScore   = 0.0;
        double ratingScore     = 0.0;
        double idleTimeScore   = 0.0;
        double sameVendorScore = 0.0;

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (ScoringRuleConfig config : configs) {
            AssignmentScoringStrategy strategy = map.get(config.factor());

            if (strategy == null) {
                log.warn("No strategy registered for factor={} — skipping", config.factor());
                continue;
            }

            double factorScore = strategy.calculateScore(context, config);
            double weight      = config.weight();

            weightedSum += factorScore * weight;
            totalWeight += weight;

            switch (config.factor()) {
                case DISTANCE    -> distanceScore   = factorScore;
                case CAPACITY    -> capacityScore   = factorScore;
                case RATING      -> ratingScore     = factorScore;
                case IDLE_TIME   -> idleTimeScore   = factorScore;
                case SAME_VENDOR -> sameVendorScore = factorScore;
            }
        }

        if (totalWeight <= 0) {
            throw new InvalidRuleException(
                    "AssignmentScoringEngine: totalWeight is zero. "
                    + "At least one enabled factor must have weight > 0.");
        }

        double finalScore = weightedSum / totalWeight;

        ScoringResult result = ScoringResult.builder()
                .distanceScore(distanceScore)
                .capacityScore(capacityScore)
                .ratingScore(ratingScore)
                .idleTimeScore(idleTimeScore)
                .sameVendorScore(sameVendorScore)
                .finalScore(finalScore)
                .build();

        log.debug("Scored partnerId={} orderId={} → {}",
                context.partner().getId(), context.order().getOrderId(), result);

        return result;
    }

    // ── Weight snapshot ───────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable snapshot of the current factor → weight mapping.
     * Reading from DB on every call ensures the snapshot reflects the live config.
     */
    @Override
    public Map<AssignmentScoreFactor, Double> getEnabledWeights() {
        List<ScoringRuleConfig> configs = assignmentScoringRuleService.getEnabledScoringConfigs();
        Map<AssignmentScoreFactor, Double> weights = new LinkedHashMap<>();
        for (ScoringRuleConfig cfg : configs) {
            weights.put(cfg.factor(), cfg.weight());
        }
        return Collections.unmodifiableMap(weights);
    }
}
