package com.routeassign.dto.request;

import com.routeassign.domain.enums.ScoringMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for PUT /api/v1/scoring-rules/{factor}
 *
 * All fields are optional — only supplied (non-null) fields are applied.
 * The controller requires at least one non-null field per call.
 *
 * <pre>
 * // Adjust distance scoring range:
 * { "maxValue": 60.0 }
 *
 * // Tune SAME_VENDOR bonus:
 * { "positiveScore": 80.0, "negativeScore": 0.0 }
 *
 * // Change RATING scale to 1–10:
 * { "minValue": 1.0, "maxValue": 10.0 }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScoringRuleRequest {

    /**
     * New relative weight for this factor.
     * Must be >= 0 when provided.
     */
    @Min(value = 0, message = "weight must be >= 0")
    private Double weight;

    /** Enable or disable this factor in the composite scoring. */
    private Boolean isEnabled;

    /** Scoring algorithm to apply to this factor's raw measurement. */
    private ScoringMethod scoringMethod;

    /**
     * Lower normalisation bound.
     * Used by LINEAR and INVERSE_LINEAR methods.
     * For RATING (NORMALIZED), this defines the minimum meaningful rating
     * so that a minimum-rated partner does not score 0 unfairly.
     */
    private Double minValue;

    /**
     * Upper normalisation bound.
     * Used by LINEAR, INVERSE_LINEAR, and NORMALIZED methods.
     * Must be > minValue (and > 0) when provided for non-BINARY factors.
     */
    private Double maxValue;

    /**
     * BINARY factors only — score returned when the boolean condition is TRUE.
     * Example: SAME_VENDOR = true → positiveScore.
     * Must be in [0, 100] when provided.
     */
    @DecimalMin(value = "0.0",   message = "positiveScore must be >= 0")
    @DecimalMax(value = "100.0", message = "positiveScore must be <= 100")
    private Double positiveScore;

    /**
     * BINARY factors only — score returned when the boolean condition is FALSE.
     * Example: SAME_VENDOR = false → negativeScore.
     * Must be in [0, 100] and <= positiveScore when provided.
     */
    @DecimalMin(value = "0.0",   message = "negativeScore must be >= 0")
    @DecimalMax(value = "100.0", message = "negativeScore must be <= 100")
    private Double negativeScore;

    /**
     * Returns {@code true} if at least one field was supplied in the request body.
     *
     * Used by the controller to reject empty PUT requests before calling the service.
     * Adding a new field to this DTO requires adding it here too — compile-time
     * enforcement is intentional (the method will fail to compile if a new field
     * is missed because the pattern is explicit rather than reflective).
     */
    public boolean hasAnyField() {
        return weight        != null
            || isEnabled     != null
            || scoringMethod != null
            || minValue      != null
            || maxValue      != null
            || positiveScore != null
            || negativeScore != null;
    }
}
