package com.routeassign.repository;

import com.routeassign.domain.entity.AssignmentScoringRule;
import com.routeassign.domain.enums.AssignmentScoreFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link AssignmentScoringRule}.
 *
 * Scoring strategies must NOT call this repository directly.
 * All access must go through {@code AssignmentScoringRuleService} so that
 * validation, error handling, and future caching stay in one place.
 */
@Repository
public interface AssignmentScoringRuleRepository extends JpaRepository<AssignmentScoringRule, Long> {

    /**
     * Returns all scoring rules that are currently enabled.
     * Used by the scoring engine to load the active factor configuration.
     */
    List<AssignmentScoringRule> findAllByIsEnabledTrue();

    /**
     * Looks up a single scoring rule by its factor, regardless of enabled status.
     * Used by the admin API to inspect and update any rule.
     */
    Optional<AssignmentScoringRule> findByFactor(AssignmentScoreFactor factor);
}
