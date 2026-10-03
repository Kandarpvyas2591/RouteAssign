package com.routeassign.service.algorithm.strategy;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.service.algorithm.ScoringRuleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Scoring strategy for the {@link AssignmentScoreFactor#RATING} factor.
 *
 * Raw measurement: partner's aggregate rating (e.g. 0.0–5.0 scale).
 *   Read directly from {@code context.partner().getRating()}.
 *   Null rating is treated as 0.0 — unrated partners score lowest on this factor.
 *
 * Direction: higher rating = higher score.
 * Default method: NORMALIZED (score = clamp(raw / maxValue)).
 *
 * The rating scale ceiling (maxValue) comes from the DB-supplied
 * {@link ScoringRuleConfig} — it is NOT hardcoded here.
 * If the rating scale changes (e.g. from 5.0 to 10.0), only the DB row
 * needs updating.
 *
 * All normalisation parameters come from the DB-supplied {@link ScoringRuleConfig}.
 */
@Slf4j
@Component
public class RatingScoringStrategy implements AssignmentScoringStrategy {

    @Override
    public AssignmentScoreFactor getFactor() {
        return AssignmentScoreFactor.RATING;
    }

    @Override
    public double calculateScore(CandidateContext context, ScoringRuleConfig config) {
        // Null-safe: unrated partners score 0 on this factor
        double raw   = context.partner().getRating() != null
                ? context.partner().getRating()
                : 0.0;
        double score = ScoringNormalizer.normalise(raw, config);

        log.trace("RATING score: partnerId={} rating={} score={:.2f}",
                context.partner().getId(), raw, score);

        return score;
    }
}
