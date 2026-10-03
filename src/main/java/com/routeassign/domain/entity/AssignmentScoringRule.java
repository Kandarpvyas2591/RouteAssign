package com.routeassign.domain.entity;

import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.domain.enums.ScoringMethod;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores the scoring configuration for each factor used by the assignment engine.
 *
 * <pre>
 * assignment_scoring_rules
 * ──────────────────────────────────────────────────────────────────────
 * id              BIGINT PK AUTO_INCREMENT
 * factor          VARCHAR(30)  UNIQUE NOT NULL  — enum name
 * weight          DOUBLE       NOT NULL         — relative weight (e.g. 40 for 40%)
 * is_enabled      BOOLEAN      NOT NULL         — exclude disabled factors from scoring
 * scoring_method  VARCHAR(30)  NOT NULL         — INVERSE_LINEAR | LINEAR | NORMALIZED | BINARY
 * min_value       DOUBLE                        — lower bound for normalisation
 * max_value       DOUBLE                        — upper bound for normalisation
 * positive_score  DOUBLE                        — BINARY: score when condition is TRUE  (e.g. 100)
 * negative_score  DOUBLE                        — BINARY: score when condition is FALSE (e.g.   0)
 * description     VARCHAR(500)
 * updated_by      VARCHAR(100)                  — audit: who last changed this rule
 * created_at      DATETIME     NOT NULL
 * updated_at      DATETIME     NOT NULL
 * ──────────────────────────────────────────────────────────────────────
 * </pre>
 *
 * Weight semantics:
 *   The engine normalises weights across all enabled factors before applying them,
 *   so absolute values (40, 20, 15...) and fractions (0.40, 0.20, 0.15...) both
 *   work correctly.  Changing one weight automatically re-proportions all others.
 *
 * minValue / maxValue semantics:
 *   Define the expected raw measurement range for normalisation formulas.
 *   Ignored for BINARY factors (use positiveScore / negativeScore instead).
 *   Example: DISTANCE minValue=0, maxValue=50 means a 0 km route scores 100,
 *            and a ≥50 km route scores 0 (INVERSE_LINEAR).
 *
 * positiveScore / negativeScore semantics (BINARY factors only):
 *   SAME_VENDOR = true  → positiveScore (default 100)
 *   SAME_VENDOR = false → negativeScore (default   0)
 *   Keeping these in DB rather than hardcoding lets the admin tune the bonus
 *   (e.g. positiveScore=80 makes same-vendor less dominant in the final score).
 */
@Entity
@Table(
    name = "assignment_scoring_rules",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_scoring_rules_factor",
        columnNames = "factor"
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentScoringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The scoring dimension this row configures.
     * Stored as the enum name string (e.g. "DISTANCE").
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "factor", nullable = false, length = 30, unique = true)
    private AssignmentScoreFactor factor;

    /**
     * Relative weight of this factor in the composite score.
     * Must be >= 0. The engine normalises across all enabled weights
     * so the sum does not need to equal 100 or 1.0.
     */
    @Column(name = "weight", nullable = false)
    private Double weight;

    /**
     * When false, this factor is excluded from scoring entirely.
     * Its weight is not counted in the normalisation denominator.
     */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    /**
     * Identifies the scoring algorithm applied to this factor's raw measurement.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_method", nullable = false, length = 30)
    private ScoringMethod scoringMethod;

    /**
     * Lower bound of the raw measurement range (used by LINEAR and INVERSE_LINEAR).
     * Null is permitted for BINARY factors.
     */
    @Column(name = "min_value")
    private Double minValue;

    /**
     * Upper bound of the raw measurement range (used by LINEAR, INVERSE_LINEAR,
     * and NORMALIZED).  Null is permitted for BINARY factors.
     */
    @Column(name = "max_value")
    private Double maxValue;

    /**
     * BINARY factors only — score returned when the condition evaluates to TRUE.
     * Example: SAME_VENDOR = true → positiveScore (default 100.0).
     * Null for non-BINARY factors; validated and required when method = BINARY.
     */
    @Column(name = "positive_score")
    private Double positiveScore;

    /**
     * BINARY factors only — score returned when the condition evaluates to FALSE.
     * Example: SAME_VENDOR = false → negativeScore (default 0.0).
     * Null for non-BINARY factors; validated and required when method = BINARY.
     */
    @Column(name = "negative_score")
    private Double negativeScore;

    /** Human-readable explanation of what this factor measures and its valid ranges. */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Audit field: who last modified this scoring rule.
     * Null until the first explicit admin update.
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
        if (this.isEnabled == null) this.isEnabled = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
