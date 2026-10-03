package com.routeassign.service.algorithm.strategy;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.service.algorithm.ScoringRuleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Scoring strategy for the {@link AssignmentScoreFactor#IDLE_TIME} factor.
 *
 * Raw measurement: minutes since the partner's last completed delivery.
 *   Provided by {@link CandidateContext#idleMinutes()}.
 *   {@link Long#MAX_VALUE} signals a partner who has never been assigned.
 *
 * Direction: longer idle time = higher score (fairness — prefer under-utilised partners).
 * Default method: LINEAR.
 *
 * Never-assigned handling:
 *   A partner who has never completed a delivery has {@code idleMinutes = Long.MAX_VALUE}.
 *   This strategy maps that sentinel to {@code config.maxValue()} from the DB, so they
 *   receive the maximum possible score for this factor — i.e. treated as if they have
 *   been idle for the entire configured scale ceiling.
 *
 *   This is a configured business rule: the maxValue in the DB defines the scale,
 *   and never-assigned partners get that maximum.  No hardcoded value is used.
 *
 * All normalisation parameters come from the DB-supplied {@link ScoringRuleConfig}.
 */
@Slf4j
@Component
public class IdleTimeScoringStrategy implements AssignmentScoringStrategy {

    @Override
    public AssignmentScoreFactor getFactor() {
        return AssignmentScoreFactor.IDLE_TIME;
    }

    @Override
    public double calculateScore(CandidateContext context, ScoringRuleConfig config) {
        double raw;

        if (context.idleMinutes() == Long.MAX_VALUE) {
            // Never-assigned partner: treat as maximally idle per the DB-configured scale.
            // ScoringNormalizer.effectiveMax returns config.maxValue(), falling back to 10080 (1 week).
            raw = ScoringNormalizer.effectiveMax(config, 10080.0);
            log.trace("IDLE_TIME: partnerId={} never assigned → using maxValue={} as raw",
                    context.partner().getId(), raw);
        } else {
            raw = (double) context.idleMinutes();
        }

        double score = ScoringNormalizer.normalise(raw, config);

        log.trace("IDLE_TIME score: partnerId={} idleMinutes={} score={:.2f}",
                context.partner().getId(), context.idleMinutes(), score);

        return score;
    }
}
