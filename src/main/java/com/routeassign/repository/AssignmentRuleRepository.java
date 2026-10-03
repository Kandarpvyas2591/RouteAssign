package com.routeassign.repository;

import com.routeassign.domain.entity.AssignmentRule;
import com.routeassign.domain.enums.AssignmentRuleKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link AssignmentRule}.
 *
 * Algorithm services must NOT call this repository directly.
 * All rule access must go through {@code AssignmentRuleService} so that
 * validation, error-handling, and future caching logic stay in one place.
 */
@Repository
public interface AssignmentRuleRepository extends JpaRepository<AssignmentRule, Long> {

    /**
     * Looks up a single rule by its canonical key, regardless of active status.
     * Used internally by the service to load or update a specific rule.
     */
    Optional<AssignmentRule> findByRuleKey(AssignmentRuleKey ruleKey);

    /**
     * Returns all rules that are currently active.
     * Useful for bulk-loading or displaying the full effective ruleset.
     */
    List<AssignmentRule> findAllByIsActiveTrue();
}
