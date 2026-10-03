package com.routeassign.domain.enums;

/**
 * Identifies the mathematical algorithm used to convert a raw factor measurement
 * into a normalised score in [0.0, 1.0].
 *
 * The method names are fixed in Java because they identify supported algorithms.
 * The parameters those algorithms operate on (minValue, maxValue) come from the
 * {@code assignment_scoring_rules} table at runtime.
 *
 * ┌──────────────────┬────────────────────────────────────────────────────────┐
 * │ Method           │ Formula (raw = raw measurement)                        │
 * ├──────────────────┼────────────────────────────────────────────────────────┤
 * │ INVERSE_LINEAR   │ 1 − clamp((raw − min) / (max − min))                  │
 * │                  │ Use when lower raw value = better score (e.g. distance)│
 * ├──────────────────┼────────────────────────────────────────────────────────┤
 * │ LINEAR           │ clamp((raw − min) / (max − min))                       │
 * │                  │ Use when higher raw value = better score               │
 * │                  │ (e.g. capacity, idle time)                             │
 * ├──────────────────┼────────────────────────────────────────────────────────┤
 * │ NORMALIZED       │ clamp(raw / max)                                       │
 * │                  │ Use when factor is already on a known fixed scale       │
 * │                  │ (e.g. rating 0–5 → max=5)                              │
 * ├──────────────────┼────────────────────────────────────────────────────────┤
 * │ BINARY           │ raw > 0 ? 1.0 : 0.0                                    │
 * │                  │ Use for boolean/presence factors (e.g. same-vendor)    │
 * └──────────────────┴────────────────────────────────────────────────────────┘
 *
 * In all formulas:  clamp(x) = max(0.0, min(1.0, x))
 */
public enum ScoringMethod {

    /**
     * Score decreases as raw value increases.
     * score = 1 − clamp((raw − minValue) / (maxValue − minValue))
     */
    INVERSE_LINEAR,

    /**
     * Score increases as raw value increases.
     * score = clamp((raw − minValue) / (maxValue − minValue))
     */
    LINEAR,

    /**
     * Raw value divided by maxValue, clamped to [0, 1].
     * score = clamp(raw / maxValue)
     */
    NORMALIZED,

    /**
     * Binary presence indicator — full score or zero.
     * score = (raw > 0) ? 1.0 : 0.0
     */
    BINARY
}
