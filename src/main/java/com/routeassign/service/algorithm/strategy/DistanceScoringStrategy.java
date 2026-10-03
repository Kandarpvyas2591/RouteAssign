package com.routeassign.service.algorithm.strategy;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.service.algorithm.ScoringRuleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Scoring strategy for the {@link AssignmentScoreFactor#DISTANCE} factor.
 *
 * Raw measurement: total route distance in km
 *   = partner→vendor distance + vendor→customer distance
 *   (pre-computed in {@link CandidateContext#totalDistanceKm()})
 *
 * Direction: lower distance = higher score.
 * Default method: INVERSE_LINEAR.
 *
 * The normalisation parameters (minValue, maxValue, scoringMethod) are read
 * exclusively from the DB-supplied {@link ScoringRuleConfig} — no thresholds
 * or weights are hardcoded here.
 *
 * Architecture:
 * <pre>
 * DistanceScoringStrategy
 *        ↓  reads pre-computed totalDistanceKm from CandidateContext
 *        ↓  reads minValue/maxValue/method from ScoringRuleConfig (DB)
 *        ↓  delegates normalisation to ScoringNormalizer
 *        ↓  returns score in [0.0, 100.0]
 * </pre>
 */
@Slf4j
@Component
public class DistanceScoringStrategy implements AssignmentScoringStrategy {

    @Override
    public AssignmentScoreFactor getFactor() {
        return AssignmentScoreFactor.DISTANCE;
    }

    @Override
    public double calculateScore(CandidateContext context, ScoringRuleConfig config) {
        double raw   = context.totalDistanceKm();
        double score = ScoringNormalizer.normalise(raw, config);

        log.trace("DISTANCE score: partnerId={} totalDist={:.2f}km score={:.2f}",
                context.partner().getId(), raw, score);

        return score;
    }
}
