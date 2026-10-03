package com.routeassign.service.impl;

import com.routeassign.domain.entity.AssignmentRule;
import com.routeassign.domain.enums.AssignmentRuleKey;
import com.routeassign.exception.InvalidRuleException;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.AssignmentRuleRepository;
import com.routeassign.service.AssignmentRuleService;
import com.routeassign.service.cache.RuleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implements {@link AssignmentRuleService} with a Redis cache-aside layer.
 *
 * Read path (cache-aside)
 * ───────────────────────
 *   1. Check Redis  → HIT: return cached value.
 *   2. MISS: read MySQL, populate Redis, return value.
 *
 * Write path (cache invalidation)
 * ────────────────────────────────
 *   1. Validate new value.
 *   2. Update MySQL.
 *   3. Evict Redis key → next read will repopulate from MySQL.
 *
 * Resilience:
 *   All Redis operations in {@link RuleCacheService} catch exceptions and log
 *   a warning, so Redis being unavailable never breaks the assignment flow —
 *   it simply means every read falls through to MySQL until Redis recovers.
 *
 * Validation layers (unchanged from Phase 1)
 * ───────────────────────────────────────────
 *   1. Type-parseability check
 *   2. Domain range check
 *   3. Cross-rule consistency
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentRuleServiceImpl implements AssignmentRuleService {

    private final AssignmentRuleRepository assignmentRuleRepository;
    private final RuleCacheService         ruleCacheService;

    // ── Typed read access (cache-aside) ───────────────────────────────────────

    @Override
    public double getDoubleRule(AssignmentRuleKey key) {
        AssignmentRule rule = loadActiveRule(key);
        return parseDouble(key, rule.getRuleValue());
    }

    @Override
    public int getIntegerRule(AssignmentRuleKey key) {
        AssignmentRule rule = loadActiveRule(key);
        return parseInteger(key, rule.getRuleValue());
    }

    @Override
    public boolean getBooleanRule(AssignmentRuleKey key) {
        AssignmentRule rule = loadActiveRule(key);
        return parseBoolean(key, rule.getRuleValue());
    }

    // ── Admin read access ─────────────────────────────────────────────────────

    @Override
    public List<AssignmentRule> getAllActiveRules() {
        return assignmentRuleRepository.findAllByIsActiveTrue();
    }

    @Override
    public AssignmentRule getRuleByKey(AssignmentRuleKey key) {
        // Admin reads always go to MySQL for fresh data
        return assignmentRuleRepository.findByRuleKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("AssignmentRule", "key", key.name()));
    }

    // ── Admin write access (cache invalidation) ───────────────────────────────

    @Override
    @Transactional
    public AssignmentRule updateRule(AssignmentRuleKey key, String newValue, String updatedBy) {
        if (newValue == null || newValue.isBlank()) {
            throw new InvalidRuleException(key.name(), "value must not be blank");
        }

        AssignmentRule rule = assignmentRuleRepository.findByRuleKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("AssignmentRule", "key", key.name()));

        String trimmed = newValue.trim();
        validateValue(key, trimmed);
        validateCrossRuleConstraints(key, trimmed);

        String oldValue = rule.getRuleValue();
        rule.setRuleValue(trimmed);
        rule.setUpdatedBy(updatedBy);

        AssignmentRule saved = assignmentRuleRepository.save(rule);

        // Evict AFTER successful MySQL commit so stale data is never served
        // from Redis if the DB write fails and rolls back.
        ruleCacheService.evictRule(key);

        log.info("Rule updated: key={} oldValue={} newValue={} by={}",
                key, oldValue, trimmed, updatedBy);

        return saved;
    }

    // ── Cache-aside load ──────────────────────────────────────────────────────

    /**
     * Cache-aside read for one active rule.
     *
     * 1. Try Redis.
     * 2. On miss (or Redis down): read MySQL, populate Redis, return.
     * 3. Throw {@link ResourceNotFoundException} if not in MySQL or inactive.
     */
    private AssignmentRule loadActiveRule(AssignmentRuleKey key) {
        // Step 1: Redis
        var cached = ruleCacheService.getRule(key);
        if (cached.isPresent()) {
            AssignmentRule rule = cached.get();
            if (Boolean.FALSE.equals(rule.getIsActive())) {
                throw new ResourceNotFoundException("AssignmentRule",
                        "key (active)", key.name() + " [rule is inactive]");
            }
            return rule;
        }

        // Step 2: MySQL
        AssignmentRule rule = assignmentRuleRepository.findByRuleKey(key)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AssignmentRule", "key", key.name()));

        if (Boolean.FALSE.equals(rule.getIsActive())) {
            throw new ResourceNotFoundException("AssignmentRule",
                    "key (active)", key.name() + " [rule is inactive]");
        }

        // Step 3: populate Redis for next call
        ruleCacheService.putRule(key, rule);

        return rule;
    }

    // ── Type parsers ──────────────────────────────────────────────────────────

    private double parseDouble(AssignmentRuleKey key, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new InvalidRuleException(key.name(),
                    "expected a decimal number but got '" + value + "'");
        }
    }

    private int parseInteger(AssignmentRuleKey key, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new InvalidRuleException(key.name(),
                    "expected an integer but got '" + value + "'");
        }
    }

    private boolean parseBoolean(AssignmentRuleKey key, String value) {
        if ("true".equalsIgnoreCase(value))  return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new InvalidRuleException(key.name(),
                "expected 'true' or 'false' but got '" + value + "'");
    }

    // ── Per-rule domain validation ────────────────────────────────────────────

    private void validateValue(AssignmentRuleKey key, String value) {
        switch (key) {
            case HOME_VENDOR_MAX_DISTANCE -> {
                double d = parseDouble(key, value);
                if (d <= 0) throw new InvalidRuleException(key.name(),
                        "distance must be > 0 km, got " + d);
            }
            case SAME_VENDOR_MAX_DISTANCE -> {
                double d = parseDouble(key, value);
                if (d <= 0) throw new InvalidRuleException(key.name(),
                        "distance must be > 0 km, got " + d);
            }
            case WORKING_HOUR_START -> {
                int h = parseInteger(key, value);
                if (h < 0 || h > 23) throw new InvalidRuleException(key.name(),
                        "hour must be in [0, 23], got " + h);
            }
            case WORKING_HOUR_END -> {
                int h = parseInteger(key, value);
                if (h < 1 || h > 24) throw new InvalidRuleException(key.name(),
                        "hour must be in [1, 24], got " + h);
            }
            case LATE_ASSIGNMENT_HOUR -> {
                int h = parseInteger(key, value);
                if (h < 0 || h > 23) throw new InvalidRuleException(key.name(),
                        "hour must be in [0, 23], got " + h);
            }
            case REST_DURATION_MINUTES -> {
                int m = parseInteger(key, value);
                if (m < 0) throw new InvalidRuleException(key.name(),
                        "rest duration must be >= 0 minutes, got " + m);
            }
            case ENABLE_BUSY_PARTNER_REUSE -> parseBoolean(key, value);
            case ASSIGNMENT_ACCEPTANCE_TIMEOUT_MINUTES -> {
                int m = parseInteger(key, value);
                if (m <= 0) throw new InvalidRuleException(key.name(),
                        "acceptance timeout must be > 0 minutes, got " + m);
            }
            case MAX_ASSIGNMENT_ATTEMPTS -> {
                int n = parseInteger(key, value);
                if (n < 1) throw new InvalidRuleException(key.name(),
                        "max assignment attempts must be >= 1, got " + n);
            }
        }
    }

    // ── Cross-rule consistency ────────────────────────────────────────────────

    private void validateCrossRuleConstraints(AssignmentRuleKey key, String value) {
        switch (key) {
            case WORKING_HOUR_START -> {
                int newStart    = parseInteger(key, value);
                int currentEnd  = safeGetInteger(AssignmentRuleKey.WORKING_HOUR_END);
                if (newStart >= currentEnd)
                    throw new InvalidRuleException(key.name(),
                            "WORKING_HOUR_START (" + newStart
                            + ") must be less than WORKING_HOUR_END (" + currentEnd + ")");
                int cutoff = safeGetInteger(AssignmentRuleKey.LATE_ASSIGNMENT_HOUR);
                if (cutoff < newStart)
                    throw new InvalidRuleException(key.name(),
                            "Changing WORKING_HOUR_START to " + newStart
                            + " would place LATE_ASSIGNMENT_HOUR (" + cutoff
                            + ") before the working day start.");
            }
            case WORKING_HOUR_END -> {
                int newEnd      = parseInteger(key, value);
                int currentStart = safeGetInteger(AssignmentRuleKey.WORKING_HOUR_START);
                if (currentStart >= newEnd)
                    throw new InvalidRuleException(key.name(),
                            "WORKING_HOUR_END (" + newEnd
                            + ") must be greater than WORKING_HOUR_START (" + currentStart + ")");
                int cutoff = safeGetInteger(AssignmentRuleKey.LATE_ASSIGNMENT_HOUR);
                if (cutoff >= newEnd)
                    throw new InvalidRuleException(key.name(),
                            "Changing WORKING_HOUR_END to " + newEnd
                            + " would place LATE_ASSIGNMENT_HOUR (" + cutoff
                            + ") at or after the new end of day.");
            }
            case LATE_ASSIGNMENT_HOUR -> {
                int newCutoff    = parseInteger(key, value);
                int currentStart = safeGetInteger(AssignmentRuleKey.WORKING_HOUR_START);
                int currentEnd   = safeGetInteger(AssignmentRuleKey.WORKING_HOUR_END);
                if (newCutoff < currentStart || newCutoff >= currentEnd)
                    throw new InvalidRuleException(key.name(),
                            "LATE_ASSIGNMENT_HOUR (" + newCutoff
                            + ") must be within [WORKING_HOUR_START=" + currentStart
                            + ", WORKING_HOUR_END=" + currentEnd + ")");
            }
            default -> { /* no cross-rule constraint */ }
        }
    }

    private int safeGetInteger(AssignmentRuleKey siblingKey) {
        return assignmentRuleRepository.findByRuleKey(siblingKey)
                .map(r -> {
                    try { return Integer.parseInt(r.getRuleValue()); }
                    catch (NumberFormatException ex) { return -1; }
                })
                .orElse(-1);
    }
}
