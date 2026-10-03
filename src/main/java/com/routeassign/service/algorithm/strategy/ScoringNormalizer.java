package com.routeassign.service.algorithm.strategy;

import com.routeassign.service.algorithm.ScoringRuleConfig;

/**
 * Stateless utility that applies the DB-configured normalisation formula
 * to a raw measurement and returns a score in [0.0, 100.0].
 *
 * All five strategies delegate to this class so every formula lives in exactly
 * one place.  The formula structure is fixed in Java (it identifies supported
 * algorithms); the parameters (minValue, maxValue, positiveScore, negativeScore)
 * always come from the DB via {@link ScoringRuleConfig}.
 *
 * <pre>
 * INVERSE_LINEAR  →  100 × ( 1 − clamp( (raw − min) / (max − min) ) )
 *                    Closest distance  → 100,  farthest relevant distance → 0
 *
 * LINEAR          →  100 × clamp( (raw − min) / (max − min) )
 *                    Maximum capacity / idle time → 100, minimum → 0
 *
 * NORMALIZED      →  100 × clamp( (raw − min) / (max − min) )
 *                    Identical to LINEAR; alias kept for semantic clarity.
 *                    Used for RATING where min/max define the rating scale
 *                    boundaries (e.g. 1–5), so a rating of 1 is not penalised
 *                    as if it were 0.
 *
 * BINARY          →  condition true  → config.positiveScore() (e.g. 100)
 *                    condition false → config.negativeScore() (e.g.   0)
 *                    Both values come from DB — not hardcoded.
 * </pre>
 *
 * In all linear formulas:  clamp(x) = max(0.0, min(1.0, x))
 */
final class ScoringNormalizer {

    private ScoringNormalizer() {}

    /**
     * Applies the scoring method defined in {@code config} to {@code raw}
     * and returns a score in [0.0, 100.0].
     *
     * @param raw    the raw measurement on its natural scale
     * @param config DB-sourced configuration for the factor being scored
     * @return score in [0.0, 100.0]; higher = better fit
     */
    static double normalise(double raw, ScoringRuleConfig config) {
        return switch (config.scoringMethod()) {

            case INVERSE_LINEAR -> {
                // Lower raw = better. score = 100 × (1 − clamp((raw − min) / (max − min)))
                double min   = config.minValue() != null ? config.minValue() : 0.0;
                double max   = config.maxValue() != null ? config.maxValue() : 1.0;
                double range = max - min;
                if (range <= 0) yield 100.0;   // degenerate range → full score
                yield 100.0 * clamp(1.0 - (raw - min) / range);
            }

            case LINEAR -> {
                // Higher raw = better. score = 100 × clamp((raw − min) / (max − min))
                double min   = config.minValue() != null ? config.minValue() : 0.0;
                double max   = config.maxValue() != null ? config.maxValue() : 1.0;
                double range = max - min;
                if (range <= 0) yield 100.0;
                yield 100.0 * clamp((raw - min) / range);
            }

            case NORMALIZED -> {
                // Semantically identical to LINEAR; uses min/max for scale boundaries.
                // Used for RATING so that the minimum rating value (e.g. 1.0) does not
                // map to 0 — the full scale is shifted to [min, max].
                double min   = config.minValue() != null ? config.minValue() : 0.0;
                double max   = config.maxValue() != null ? config.maxValue() : 1.0;
                double range = max - min;
                if (range <= 0) yield 100.0;
                yield 100.0 * clamp((raw - min) / range);
            }

            case BINARY -> {
                // Condition true  → positiveScore from DB (default 100)
                // Condition false → negativeScore from DB (default   0)
                // These are NOT hardcoded — the admin controls both values.
                double pos = config.positiveScore() != null ? config.positiveScore() : 100.0;
                double neg = config.negativeScore() != null ? config.negativeScore() :   0.0;
                yield raw > 0.0 ? pos : neg;
            }
        };
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /** Clamps {@code value} to [0.0, 1.0]. */
    static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Returns the effective maxValue from config, falling back to {@code fallback}
     * when the field is null.
     * Used by IdleTimeScoringStrategy to cap never-assigned partner idle time
     * at the configured maximum rather than using a hardcoded constant.
     */
    static double effectiveMax(ScoringRuleConfig config, double fallback) {
        return config.maxValue() != null ? config.maxValue() : fallback;
    }
}
