package com.routeassign.service.impl;

import com.routeassign.domain.entity.AssignmentScoringRule;
import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.domain.enums.ScoringMethod;
import com.routeassign.exception.InvalidRuleException;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.AssignmentScoringRuleRepository;
import com.routeassign.service.AssignmentScoringRuleService;
import com.routeassign.service.algorithm.ScoringRuleConfig;
import com.routeassign.service.cache.RuleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements {@link AssignmentScoringRuleService} with a Redis cache-aside layer.
 *
 * Read path (cache-aside)
 * ───────────────────────
 *   {@link #getEnabledScoringConfigs()} — the hot path called on every assignment:
 *     1. Check Redis key  routeassign:scoring:all-enabled
 *     2. HIT  → validate cached list, return.
 *     3. MISS → query MySQL, validate, store in Redis, return.
 *
 * Write path (cache invalidation)
 * ────────────────────────────────
 *   {@link #updateRule()} after a successful MySQL save:
 *     - Evict  routeassign:scoring:{FACTOR}       (single-factor key)
 *     - Evict  routeassign:scoring:all-enabled    (full list key)
 *   Next call to getEnabledScoringConfigs() repopulates both from MySQL.
 *
 * Resilience:
 *   All Redis operations in {@link RuleCacheService} swallow exceptions so
 *   Redis unavailability never prevents an assignment from completing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentScoringRuleServiceImpl implements AssignmentScoringRuleService {

    private final AssignmentScoringRuleRepository scoringRuleRepository;
    private final RuleCacheService                ruleCacheService;

    // ── Scoring engine access (cache-aside hot path) ──────────────────────────

    @Override
    public List<ScoringRuleConfig> getEnabledScoringConfigs() {

        // Step 1: try Redis
        var cached = ruleCacheService.getAllEnabledScoringConfigs();
        if (cached.isPresent()) {
            // Cached configs were validated when written — return directly.
            // The TTL safety mechanism and write-path eviction ensure freshness.
            log.debug("Returning {} scoring configs from Redis cache",
                    cached.get().size());
            return cached.get();
        }

        // Step 2: MySQL
        List<AssignmentScoringRule> enabled = scoringRuleRepository.findAllByIsEnabledTrue();
        validateConfigList(enabled);

        List<ScoringRuleConfig> configs = enabled.stream()
                .map(r -> new ScoringRuleConfig(
                        r.getFactor(), r.getWeight(), r.getScoringMethod(),
                        r.getMinValue(), r.getMaxValue(),
                        r.getPositiveScore(), r.getNegativeScore()))
                .toList();

        // Step 3: populate Redis
        ruleCacheService.putAllEnabledScoringConfigs(configs);

        return configs;
    }

    // ── Admin read access ─────────────────────────────────────────────────────

    @Override
    public List<AssignmentScoringRule> getAllRules() {
        return scoringRuleRepository.findAll();
    }

    @Override
    public AssignmentScoringRule getRuleByFactor(AssignmentScoreFactor factor) {
        return scoringRuleRepository.findByFactor(factor)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AssignmentScoringRule", "factor", factor.name()));
    }

    // ── Admin write access (cache invalidation) ───────────────────────────────

    @Override
    @Transactional
    public AssignmentScoringRule updateRule(AssignmentScoreFactor factor,
                                            Double        weight,
                                            Boolean       isEnabled,
                                            ScoringMethod scoringMethod,
                                            Double        minValue,
                                            Double        maxValue,
                                            Double        positiveScore,
                                            Double        negativeScore,
                                            String        updatedBy) {

        AssignmentScoringRule rule = scoringRuleRepository.findByFactor(factor)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AssignmentScoringRule", "factor", factor.name()));

        Double        effectiveWeight  = weight        != null ? weight        : rule.getWeight();
        Boolean       effectiveEnabled = isEnabled     != null ? isEnabled     : rule.getIsEnabled();
        ScoringMethod effectiveMethod  = scoringMethod != null ? scoringMethod : rule.getScoringMethod();
        Double        effectiveMin     = minValue      != null ? minValue      : rule.getMinValue();
        Double        effectiveMax     = maxValue      != null ? maxValue      : rule.getMaxValue();
        Double        effectivePos     = positiveScore != null ? positiveScore : rule.getPositiveScore();
        Double        effectiveNeg     = negativeScore != null ? negativeScore : rule.getNegativeScore();

        validateFields(factor, effectiveWeight, effectiveMethod,
                effectiveMin, effectiveMax, effectivePos, effectiveNeg);
        validateSystemState(factor, effectiveWeight, effectiveEnabled);

        rule.setWeight(effectiveWeight);
        rule.setIsEnabled(effectiveEnabled);
        rule.setScoringMethod(effectiveMethod);
        rule.setMinValue(effectiveMin);
        rule.setMaxValue(effectiveMax);
        rule.setPositiveScore(effectivePos);
        rule.setNegativeScore(effectiveNeg);
        rule.setUpdatedBy(updatedBy);

        AssignmentScoringRule saved = scoringRuleRepository.save(rule);

        // Evict AFTER successful MySQL save — this also evicts all-enabled list
        ruleCacheService.evictScoringConfig(factor);

        log.info("Scoring rule updated: factor={} weight={} enabled={} method={} by={}",
                factor, effectiveWeight, effectiveEnabled, effectiveMethod, updatedBy);

        return saved;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validates a list of {@link AssignmentScoringRule} entities from MySQL.
     * Called both on the MySQL read path and (lightly) on the Redis cache hit
     * path to guard against stale or corrupted cached data.
     */
    private void validateConfigList(List<AssignmentScoringRule> enabled) {
        if (enabled.isEmpty()) {
            throw new InvalidRuleException(
                    "Scoring configuration is invalid: no enabled scoring factors found. "
                    + "At least one factor must be enabled.");
        }

        Set<AssignmentScoreFactor> seen = enabled.stream()
                .map(AssignmentScoringRule::getFactor)
                .collect(Collectors.toSet());
        if (seen.size() != enabled.size()) {
            throw new InvalidRuleException(
                    "Scoring configuration is invalid: duplicate factor entries detected.");
        }

        double totalWeight = enabled.stream()
                .mapToDouble(AssignmentScoringRule::getWeight)
                .sum();
        if (totalWeight <= 0) {
            throw new InvalidRuleException(
                    "Scoring configuration is invalid: total enabled weight is " + totalWeight
                    + ". At least one enabled factor must have weight > 0.");
        }

        for (AssignmentScoringRule rule : enabled) {
            validateFields(rule.getFactor(), rule.getWeight(), rule.getScoringMethod(),
                    rule.getMinValue(), rule.getMaxValue(),
                    rule.getPositiveScore(), rule.getNegativeScore());
        }
    }

    private void validateFields(AssignmentScoreFactor factor,
                                double weight, ScoringMethod method,
                                Double min, Double max,
                                Double positiveScore, Double negativeScore) {

        if (weight < 0) throw new InvalidRuleException(factor.name(),
                "weight must be >= 0, got " + weight);

        if (method == ScoringMethod.BINARY) {
            if (positiveScore == null) throw new InvalidRuleException(factor.name(),
                    "positiveScore is required for BINARY scoring method");
            if (negativeScore == null) throw new InvalidRuleException(factor.name(),
                    "negativeScore is required for BINARY scoring method");
            if (positiveScore < 0 || positiveScore > 100) throw new InvalidRuleException(factor.name(),
                    "positiveScore must be in [0, 100], got " + positiveScore);
            if (negativeScore < 0 || negativeScore > 100) throw new InvalidRuleException(factor.name(),
                    "negativeScore must be in [0, 100], got " + negativeScore);
            if (negativeScore > positiveScore) throw new InvalidRuleException(factor.name(),
                    "negativeScore (" + negativeScore + ") must be <= positiveScore (" + positiveScore + ")");
            return;
        }

        if (max == null || max <= 0) throw new InvalidRuleException(factor.name(),
                "maxValue must be > 0 for method " + method + ", got " + max);

        if (method == ScoringMethod.NORMALIZED) {
            if (min != null) {
                if (min < 0) throw new InvalidRuleException(factor.name(),
                        "minValue must be >= 0 for NORMALIZED, got " + min);
                if (min >= max) throw new InvalidRuleException(factor.name(),
                        "minValue (" + min + ") must be < maxValue (" + max + ") for NORMALIZED");
            }
            return;
        }

        if (min == null) throw new InvalidRuleException(factor.name(),
                "minValue is required for method " + method);
        if (min >= max) throw new InvalidRuleException(factor.name(),
                "minValue (" + min + ") must be < maxValue (" + max + ") for method " + method);
    }

    private void validateSystemState(AssignmentScoreFactor changedFactor,
                                     double newWeight, boolean willBeEnabled) {

        double simulatedTotal = scoringRuleRepository.findAllByIsEnabledTrue().stream()
                .filter(r -> !r.getFactor().equals(changedFactor))
                .mapToDouble(AssignmentScoringRule::getWeight)
                .sum();

        if (willBeEnabled) simulatedTotal += newWeight;

        if (simulatedTotal <= 0) throw new InvalidRuleException(changedFactor.name(),
                "This change would result in total enabled weight of " + simulatedTotal
                + ". At least one enabled factor must have weight > 0.");
    }
}
