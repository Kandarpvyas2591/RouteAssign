package com.routeassign.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit record that captures the full scoring breakdown AND the weight snapshot
 * used at the time of every assignment decision.
 *
 * <pre>
 * assignment_decision
 * ─────────────────────────────────────────────────────────────────────────────
 * id                     BIGINT PK AUTO_INCREMENT
 * delivery_assignment_id BIGINT FK → delivery_assignment(id) UNIQUE
 *
 * — Winner's factor scores (0–100 each)
 * distance_score         DOUBLE
 * capacity_score         DOUBLE
 * rating_score           DOUBLE
 * idle_time_score        DOUBLE
 * same_vendor_score      DOUBLE
 * final_score            DOUBLE
 *
 * — Weight snapshot (captured at decision time)
 * distance_weight        DOUBLE   — DB weight value at the moment of assignment
 * capacity_weight        DOUBLE
 * rating_weight          DOUBLE
 * idle_time_weight       DOUBLE
 * same_vendor_weight     DOUBLE
 *
 * decided_at             DATETIME NOT NULL
 * ─────────────────────────────────────────────────────────────────────────────
 * </pre>
 *
 * The weight snapshot answers: "what weights were active when this assignment
 * was made?" — so that changing weights later does not obscure historical
 * reasoning.
 *
 * Per-candidate breakdowns (including rejected candidates) are stored
 * in {@link AssignmentDecisionCandidate}.
 */
@Entity
@Table(name = "assignment_decision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The live assignment this decision corresponds to.
     * One-to-one: each assignment has exactly one decision record.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_assignment_id", nullable = false, unique = true)
    private DeliveryAssignment deliveryAssignment;

    // ── Winner's factor scores ────────────────────────────────────────────────

    @Column(name = "distance_score")
    private Double distanceScore;

    @Column(name = "capacity_score")
    private Double capacityScore;

    @Column(name = "rating_score")
    private Double ratingScore;

    @Column(name = "idle_time_score")
    private Double idleTimeScore;

    @Column(name = "same_vendor_score")
    private Double sameVendorScore;

    /** Weighted composite final score (0–100). Determined the winner. */
    @Column(name = "final_score")
    private Double finalScore;

    // ── Weight snapshot (captured from DB at decision time) ───────────────────

    /**
     * The DISTANCE factor weight that was active when this decision was made.
     * Stored so that later admin changes to weights do not obscure historical context.
     */
    @Column(name = "distance_weight")
    private Double distanceWeight;

    /** Snapshot of the CAPACITY factor weight at decision time. */
    @Column(name = "capacity_weight")
    private Double capacityWeight;

    /** Snapshot of the RATING factor weight at decision time. */
    @Column(name = "rating_weight")
    private Double ratingWeight;

    /** Snapshot of the IDLE_TIME factor weight at decision time. */
    @Column(name = "idle_time_weight")
    private Double idleTimeWeight;

    /** Snapshot of the SAME_VENDOR factor weight at decision time. */
    @Column(name = "same_vendor_weight")
    private Double sameVendorWeight;

    // ── Audit ─────────────────────────────────────────────────────────────────

    @Column(name = "decided_at", nullable = false, updatable = false)
    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        this.decidedAt = LocalDateTime.now();
    }
}
