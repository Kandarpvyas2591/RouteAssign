package com.routeassign.service.algorithm.strategy;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.service.algorithm.ScoringRuleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Scoring strategy for the {@link AssignmentScoreFactor#CAPACITY} factor.
 *
 * Raw measurement: remaining carrying capacity in kg
 *   = partner.capacity − partner.currentAssignedWeight
 *   (pre-computed in {@link CandidateContext#remainingCapacityKg()})
 *
 * Direction: more remaining capacity = higher score.
 * Default method: LINEAR.
 *
 * Note: eligibility filtering already guarantees that
 * {@code remainingCapacity >= order.totalWeight} before any candidate
 * reaches the scoring engine. The scoring strategy further differentiates
 * among eligible partners by how much spare capacity they have.
 *
 * All normalisation parameters come from the DB-supplied {@link ScoringRuleConfig}.
 */
@Slf4j
@Component
public class CapacityScoringStrategy implements AssignmentScoringStrategy {

    @Override
    public AssignmentScoreFactor getFactor() {
        return AssignmentScoreFactor.CAPACITY;
    }

    @Override
    public double calculateScore(CandidateContext context, ScoringRuleConfig config) {
        double raw   = context.remainingCapacityKg();
        double score = ScoringNormalizer.normalise(raw, config);

        log.trace("CAPACITY score: partnerId={} remainingCapacity={:.2f}kg score={:.2f}",
                context.partner().getId(), raw, score);

        return score;
    }
}
