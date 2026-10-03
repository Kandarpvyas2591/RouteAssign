package com.routeassign.domain.entity;

import com.routeassign.domain.enums.RejectionReason;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Per-candidate audit record for an assignment decision.
 *
 * Answers the question: <em>"Why was Partner X not chosen for Order Y?"</em>
 *
 * <pre>
 * assignment_decision_candidate
 * ──────────────────────────────────────────────────────────────────────────────
 * id                  BIGINT PK AUTO_INCREMENT
 * decision_id         BIGINT FK → assignment_decision(id)
 * partner_id          BIGINT    — snapshot of the partner's DB id
 * partner_name        VARCHAR   — snapshot of the partner's username at decision time
 * eligible            BOOLEAN   — passed eligibility check?
 * rejection_reason    VARCHAR   — null when eligible=true
 * distance_score      DOUBLE    — null when eligible=false
 * capacity_score      DOUBLE
 * rating_score        DOUBLE
 * idle_time_score     DOUBLE
 * same_vendor_score   DOUBLE
 * final_score         DOUBLE
 * is_winner           BOOLEAN   — true for exactly one row per decision
 * evaluated_at        DATETIME
 * ──────────────────────────────────────────────────────────────────────────────
 * </pre>
 *
 * A row is created for every partner that was considered during selection:
 * <ul>
 *   <li>Rejected candidates: eligible=false, scores are null.</li>
 *   <li>Eligible but not selected: eligible=true, scores populated, isWinner=false.</li>
 *   <li>Winner: eligible=true, scores populated, isWinner=true.</li>
 * </ul>
 */
@Entity
@Table(name = "assignment_decision_candidate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentDecisionCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The parent decision record this candidate was part of.
     * Many candidates belong to one decision (one per order).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_id", nullable = false)
    private AssignmentDecision decision;

    // ── Partner snapshot ──────────────────────────────────────────────────────

    /**
     * The partner's database id at evaluation time.
     * Stored as a plain column (not a FK) so this record remains readable
     * even if the partner's account is later deleted.
     */
    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    /**
     * The partner's username at the time of the decision (for human-readable
     * audit logs without requiring a JOIN to UserDetails).
     */
    @Column(name = "partner_name", length = 100)
    private String partnerName;

    // ── Eligibility ───────────────────────────────────────────────────────────

    /**
     * Whether this partner passed all eligibility checks.
     * If false, the scoring fields will all be null.
     */
    @Column(name = "eligible", nullable = false)
    private Boolean eligible;

    /**
     * Reason code explaining why the partner was rejected.
     * Null when {@code eligible = true}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 50)
    private RejectionReason rejectionReason;

    // ── Scoring (null for rejected candidates) ────────────────────────────────

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

    /** Weighted composite final score; null for rejected candidates. */
    @Column(name = "final_score")
    private Double finalScore;

    // ── Outcome ───────────────────────────────────────────────────────────────

    /**
     * True for the one candidate that was ultimately selected as the delivery
     * partner. False for all other rows in the same decision group.
     */
    @Column(name = "is_winner", nullable = false)
    private Boolean isWinner;

    // ── Audit ─────────────────────────────────────────────────────────────────

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private LocalDateTime evaluatedAt;

    @PrePersist
    protected void onCreate() {
        this.evaluatedAt = LocalDateTime.now();
        if (this.isWinner == null) this.isWinner = false;
    }
}
