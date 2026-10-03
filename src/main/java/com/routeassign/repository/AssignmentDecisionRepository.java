package com.routeassign.repository;

import com.routeassign.domain.entity.AssignmentDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for {@link AssignmentDecision} audit records.
 */
@Repository
public interface AssignmentDecisionRepository extends JpaRepository<AssignmentDecision, Long> {

    /**
     * Returns the scoring decision for a given delivery assignment.
     * Used by the dashboard and audit API to retrieve the "why" behind
     * a specific partner selection.
     */
    Optional<AssignmentDecision> findByDeliveryAssignment_Id(Long deliveryAssignmentId);
}
