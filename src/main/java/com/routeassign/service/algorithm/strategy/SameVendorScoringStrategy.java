package com.routeassign.service.algorithm.strategy;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.service.algorithm.ScoringRuleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Scoring strategy for the {@link AssignmentScoreFactor#SAME_VENDOR} factor.
 *
 * Raw measurement: binary presence indicator.
 *   {@code context.isSameVendor()} is true if the partner already has an active
 *   assignment from the same vendor as this order.
 *
 * Direction: same-vendor assignment = maximum score (route consolidation bonus).
 * Default method: BINARY (score = 100.0 if same vendor, else 0.0).
 *
 * The score values for same-vendor and different-vendor partners are determined
 * by the {@code ScoringMethod.BINARY} formula — they are not hardcoded here.
 * Changing the method to LINEAR or NORMALIZED would allow a partial score,
 * which can be done through the admin API without code changes.
 *
 * All normalisation parameters come from the DB-supplied {@link ScoringRuleConfig}.
 */
@Slf4j
@Component
public class SameVendorScoringStrategy implements AssignmentScoringStrategy {

    @Override
    public AssignmentScoreFactor getFactor() {
        return AssignmentScoreFactor.SAME_VENDOR;
    }

    @Override
    public double calculateScore(CandidateContext context, ScoringRuleConfig config) {
        // Convert boolean to a numeric raw value: 1.0 = same vendor, 0.0 = different
        double raw   = context.isSameVendor() ? 1.0 : 0.0;
        double score = ScoringNormalizer.normalise(raw, config);

        log.trace("SAME_VENDOR score: partnerId={} isSameVendor={} score={:.2f}",
                context.partner().getId(), context.isSameVendor(), score);

        return score;
    }
}
