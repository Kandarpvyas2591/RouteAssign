package com.routeassign.repository;

import com.routeassign.domain.entity.AssignmentDecisionCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link AssignmentDecisionCandidate} audit records.
 *
 * Used by the admin dashboard and debugging tools to answer:
 * "Why was Partner X chosen instead of Partner Y for Order Z?"
 */
@Repository
public interface AssignmentDecisionCandidateRepository
        extends JpaRepository<AssignmentDecisionCandidate, Long> {

    /**
     * Returns all candidate evaluations for a given parent decision.
     * Includes both eligible (scored) and rejected (unscored) candidates.
     */
    List<AssignmentDecisionCandidate> findAllByDecision_Id(Long decisionId);

    /**
     * Returns only the winning candidate for a given decision.
     * There should be exactly one winner per decision.
     */
    Optional<AssignmentDecisionCandidate> findByDecision_IdAndIsWinnerTrue(Long decisionId);

    /**
     * Returns all decisions that evaluated a specific partner.
     * Useful for analysing a partner's historical selection/rejection patterns.
     */
    List<AssignmentDecisionCandidate> findAllByPartnerId(Long partnerId);

    /**
     * Returns all candidate rows where the partner was rejected.
     * Useful for admin troubleshooting ("why is this partner never chosen?").
     */
    List<AssignmentDecisionCandidate> findAllByPartnerIdAndEligibleFalse(Long partnerId);
}
