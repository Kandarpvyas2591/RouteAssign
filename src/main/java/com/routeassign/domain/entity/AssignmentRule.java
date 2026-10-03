package com.routeassign.domain.entity;

import com.routeassign.domain.enums.AssignmentRuleKey;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persists every configurable business rule that drives the assignment engine.
 *
 * <pre>
 * assignment_rules
 * ─────────────────────────────────────────────────────────────────────
 * id           BIGINT PK AUTO_INCREMENT
 * rule_key     VARCHAR(100) UNIQUE NOT NULL   — enum name
 * rule_value   VARCHAR(255)        NOT NULL   — stored as text
 * value_type   VARCHAR(20)         NOT NULL   — DOUBLE | INTEGER | BOOLEAN
 * description  VARCHAR(500)
 * is_active    BOOLEAN             NOT NULL
 * updated_by   VARCHAR(100)                   — audit: who made the last change
 * created_at   DATETIME            NOT NULL
 * updated_at   DATETIME            NOT NULL
 * ─────────────────────────────────────────────────────────────────────
 * </pre>
 *
 * Rules are read at query time (no in-process cache in Phase 1).
 * To change a rule: call PUT /api/v1/assignment-rules/{key} — no restart required.
 */
@Entity
@Table(
    name = "assignment_rules",
    uniqueConstraints = @UniqueConstraint(name = "uq_assignment_rules_key", columnNames = "rule_key")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Enum-backed key — uniquely identifies this rule across the whole system.
     * Stored as the enum name string (e.g. "HOME_VENDOR_MAX_DISTANCE").
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_key", nullable = false, length = 100, unique = true)
    private AssignmentRuleKey ruleKey;

    /**
     * The rule's current value, always stored as a UTF-8 string.
     * The service layer is responsible for parsing it into the correct Java type.
     */
    @Column(name = "rule_value", nullable = false, length = 255)
    private String ruleValue;

    /**
     * Declares how {@link #ruleValue} should be interpreted.
     * Allowed values: {@code DOUBLE}, {@code INTEGER}, {@code BOOLEAN}.
     */
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    /**
     * Human-readable explanation of what this rule controls and its valid range.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Soft-disable flag. Only active rules ({@code is_active = true}) are
     * returned by the service; inactive rules are retained for audit purposes.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /**
     * Audit field: username or identifier of whoever last modified this rule.
     * Populated via the admin API. Null until the first explicit update.
     */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
