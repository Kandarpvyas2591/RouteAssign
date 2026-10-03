package com.routeassign.dto.response;

import com.routeassign.domain.entity.AssignmentScoringRule;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only view of an {@link AssignmentScoringRule} for API consumers.
 *
 * Does not expose the internal DB id — the factor name is the natural external key.
 *
 * {@code positiveScore} and {@code negativeScore} are only populated for BINARY
 * factors; they are omitted from the JSON payload for other factors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScoringRuleResponse {

    private String  factor;
    private Double  weight;
    private boolean enabled;
    private String  scoringMethod;

    /** Lower normalisation bound (LINEAR / INVERSE_LINEAR / NORMALIZED). Null for BINARY. */
    private Double minValue;

    /** Upper normalisation bound (LINEAR / INVERSE_LINEAR / NORMALIZED). Null for BINARY. */
    private Double maxValue;

    /**
     * BINARY only — score when the condition is TRUE (e.g. same vendor = true).
     * Null for non-BINARY factors.
     */
    private Double positiveScore;

    /**
     * BINARY only — score when the condition is FALSE.
     * Null for non-BINARY factors.
     */
    private Double negativeScore;

    private String        description;
    private String        updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Maps an {@link AssignmentScoringRule} entity to this response DTO. */
    public static ScoringRuleResponse from(AssignmentScoringRule rule) {
        return ScoringRuleResponse.builder()
                .factor(rule.getFactor().name())
                .weight(rule.getWeight())
                .enabled(Boolean.TRUE.equals(rule.getIsEnabled()))
                .scoringMethod(rule.getScoringMethod().name())
                .minValue(rule.getMinValue())
                .maxValue(rule.getMaxValue())
                .positiveScore(rule.getPositiveScore())
                .negativeScore(rule.getNegativeScore())
                .description(rule.getDescription())
                .updatedBy(rule.getUpdatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
