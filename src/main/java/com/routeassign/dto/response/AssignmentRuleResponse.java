package com.routeassign.dto.response;

import com.routeassign.domain.entity.AssignmentRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only view of an {@link AssignmentRule} entity for API consumers.
 *
 * Does not expose the internal DB id — the ruleKey is the natural identifier
 * for external callers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentRuleResponse {

    private String ruleKey;
    private String ruleValue;
    private String valueType;
    private String description;
    private boolean active;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Maps an {@link AssignmentRule} entity to this response DTO. */
    public static AssignmentRuleResponse from(AssignmentRule rule) {
        return AssignmentRuleResponse.builder()
                .ruleKey(rule.getRuleKey().name())
                .ruleValue(rule.getRuleValue())
                .valueType(rule.getValueType())
                .description(rule.getDescription())
                .active(Boolean.TRUE.equals(rule.getIsActive()))
                .updatedBy(rule.getUpdatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
