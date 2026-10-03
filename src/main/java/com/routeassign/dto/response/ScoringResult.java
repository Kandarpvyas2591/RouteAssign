package com.routeassign.dto.response;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Complete scoring outcome for one candidate partner evaluated against one order.
 *
 * Contains both the individual per-factor scores (0–100 scale) and the
 * weighted {@link #finalScore} that the engine uses for ranking.
 *
 * Scores are on a 0–100 scale (not 0–1) so they read naturally in API
 * responses and in the {@code assignment_decision} audit table.
 *
 * Example:
 * <pre>
 * {
 *   "distanceScore":    72.0,
 *   "capacityScore":    85.0,
 *   "ratingScore":      60.0,
 *   "idleTimeScore":    90.0,
 *   "sameVendorScore": 100.0,
 *   "finalScore":       78.6
 * }
 * </pre>
 */
@Getter
@Builder
public class ScoringResult {

    /** Score for the DISTANCE factor (0–100). Higher = shorter route. */
    private final double distanceScore;

    /** Score for the CAPACITY factor (0–100). Higher = more spare capacity. */
    private final double capacityScore;

    /** Score for the RATING factor (0–100). Higher = better partner rating. */
    private final double ratingScore;

    /** Score for the IDLE_TIME factor (0–100). Higher = longer idle / more rest. */
    private final double idleTimeScore;

    /** Score for the SAME_VENDOR factor (0–100). 100 = same vendor, 0 = different. */
    private final double sameVendorScore;

    /**
     * Weighted composite score (0–100).
     *
     * Formula (engine applies normalised weights):
     * <pre>
     * finalScore = Σ( factorScore_i × (weight_i / totalEnabledWeight) )
     * </pre>
     *
     * Disabled factors contribute 0 to both numerator and denominator.
     */
    private final double finalScore;

    /**
     * Convenience accessor — returns the per-factor score for a given factor.
     * Returns 0.0 if the factor was not computed (e.g. disabled).
     */
    public double getFactorScore(AssignmentScoreFactor factor) {
        return switch (factor) {
            case DISTANCE    -> distanceScore;
            case CAPACITY    -> capacityScore;
            case RATING      -> ratingScore;
            case IDLE_TIME   -> idleTimeScore;
            case SAME_VENDOR -> sameVendorScore;
        };
    }

    /**
     * Returns all per-factor scores as an immutable map.
     * Useful for serialisation and the {@code assignment_decision} audit record.
     */
    public Map<AssignmentScoreFactor, Double> toFactorScoreMap() {
        Map<AssignmentScoreFactor, Double> map = new EnumMap<>(AssignmentScoreFactor.class);
        map.put(AssignmentScoreFactor.DISTANCE,    distanceScore);
        map.put(AssignmentScoreFactor.CAPACITY,    capacityScore);
        map.put(AssignmentScoreFactor.RATING,      ratingScore);
        map.put(AssignmentScoreFactor.IDLE_TIME,   idleTimeScore);
        map.put(AssignmentScoreFactor.SAME_VENDOR, sameVendorScore);
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String toString() {
        return "ScoringResult{dist=%.1f cap=%.1f rat=%.1f idle=%.1f sv=%.1f final=%.2f}"
                .formatted(distanceScore, capacityScore, ratingScore,
                           idleTimeScore, sameVendorScore, finalScore);
    }
}
