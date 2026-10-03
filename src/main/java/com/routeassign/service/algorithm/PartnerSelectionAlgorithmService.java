package com.routeassign.service.algorithm;

import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.UserDetails;
import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.dto.response.ScoringResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Algorithm 4 — Main Delivery Partner Selection Orchestrator.
 *
 * Responsibility: WHAT to do, not HOW to score.
 *
 * <pre>
 * 1. Find all active delivery partners (minus any excluded by AssignmentContext).
 * 2. Run eligibility checks — produce {@link EligibilityResult} for each.
 * 3. For every eligible partner, build an {@link AssignmentCandidate} with
 *    pre-computed distance, capacity, idle time, and ETA.
 * 4. Score every eligible candidate via {@link AssignmentScoringEngine}.
 * 5. Select the highest-scoring candidate.
 *    Tie-breakers: longest idle time → random.
 * 6. Persist {@code AssignmentDecision} (winner scores + weight snapshot)
 *    and {@code AssignmentDecisionCandidate} rows for every evaluated partner.
 * </pre>
 *
 * No scoring formula, no distance formula, no capacity formula belongs here.
 */
public interface PartnerSelectionAlgorithmService {

    /**
     * Selects the best eligible delivery partner for the given context.
     *
     * {@link AssignmentContext} carries:
     * <ul>
     *   <li>the order to be assigned</li>
     *   <li>partner IDs to exclude (previously failed partners)</li>
     *   <li>the attempt number (1 = initial, 2+ = reassignment)</li>
     *   <li>the reason the previous attempt failed (for audit)</li>
     * </ul>
     *
     * @param context assignment context including order and exclusion list
     * @return the selection result, or empty if no eligible partner exists
     */
    Optional<PartnerSelectionResult> selectPartnerWithEta(AssignmentContext context);

    /**
     * Convenience overload — wraps the order in a fresh initial-assignment context
     * (attempt 1, no exclusions). All existing call sites continue to compile.
     *
     * @param order the newly placed order
     * @return the selection result, or empty if no eligible partner exists
     */
    default Optional<PartnerSelectionResult> selectPartnerWithEta(Order order) {
        return selectPartnerWithEta(new AssignmentContext(order));
    }

    /**
     * Persists the complete audit trail for one assignment decision:
     * <ul>
     *   <li>One {@code AssignmentDecision} row (winner scores + weight snapshot).</li>
     *   <li>One {@code AssignmentDecisionCandidate} row per partner evaluated.</li>
     * </ul>
     *
     * Must be called AFTER the {@code DeliveryAssignment} row is saved
     * (FK constraint requires the row to exist first).
     */
    void saveDecision(DeliveryAssignment               assignment,
                      ScoringResult                    result,
                      Map<AssignmentScoreFactor, Double> weightSnapshot,
                      List<EligibilityResult>          allEligibility,
                      List<ScoredCandidate>            scoredCandidates,
                      Long                             winnerPartnerId);

    /**
     * Convenience method — returns just the selected partner.
     */
    default Optional<UserDetails> selectPartner(AssignmentContext context) {
        return selectPartnerWithEta(context).map(PartnerSelectionResult::partner);
    }

    // ── Result record ─────────────────────────────────────────────────────────

    record PartnerSelectionResult(
            UserDetails                       partner,
            LocalDateTime                     eta,
            boolean                           isBusy,
            LocalDateTime                     workStartTime,
            ScoringResult                     scoringResult,
            Map<AssignmentScoreFactor, Double> weightSnapshot,
            List<EligibilityResult>           allEligibility,
            List<ScoredCandidate>             scoredCandidates
    ) {}

    // ── Scored candidate pair ─────────────────────────────────────────────────

    record ScoredCandidate(
            AssignmentCandidate candidate,
            ScoringResult       scoringResult
    ) {}
}
