package com.routeassign.service.cache;

import com.routeassign.config.RedisConfig;
import com.routeassign.domain.entity.AssignmentRule;
import com.routeassign.domain.enums.AssignmentRuleKey;
import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.service.algorithm.ScoringRuleConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Centralised Redis access layer for assignment rule and scoring rule caching.
 *
 * Neither the algorithm services nor the strategies touch Redis directly —
 * all cache operations go through this service.
 *
 * <pre>
 * AssignmentRuleServiceImpl
 *        ↓
 * RuleCacheService   ← get / put / evict
 *        ↓
 * RedisTemplate
 *        ↓
 * Redis  (localhost:6379)
 * </pre>
 *
 * Key namespace
 * ─────────────
 *   Assignment rules:       routeassign:rule:{RULE_KEY}
 *   Single scoring rule:    routeassign:scoring:{FACTOR}
 *   All enabled configs:    routeassign:scoring:all-enabled
 *
 * Serialization: JSON (configured in {@link RedisConfig}).
 * TTL: read from {@link RedisConfig} — 300 s by default.
 *
 * Design principle:
 *   This class owns ONLY the Redis read/write/evict operations.
 *   It has no knowledge of MySQL, validation, or business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConfig                   redisConfig;

    // ── Key constants ─────────────────────────────────────────────────────────

    private static final String RULE_PREFIX            = "routeassign:rule:";
    private static final String SCORING_PREFIX         = "routeassign:scoring:";
    private static final String SCORING_ALL_ENABLED_KEY = "routeassign:scoring:all-enabled";

    // ── Assignment rule cache ─────────────────────────────────────────────────

    /**
     * Returns the cached {@link AssignmentRule} for the given key, or empty if
     * not present in Redis.
     */
    public Optional<AssignmentRule> getRule(AssignmentRuleKey key) {
        String cacheKey = RULE_PREFIX + key.name();
        try {
            Object value = redisTemplate.opsForValue().get(cacheKey);
            if (value instanceof AssignmentRule rule) {
                log.debug("Cache HIT rule key={}", key);
                return Optional.of(rule);
            }
        } catch (Exception ex) {
            log.warn("Redis read failed for rule key={}: {} — falling back to MySQL",
                    key, ex.getMessage());
        }
        log.debug("Cache MISS rule key={}", key);
        return Optional.empty();
    }

    /**
     * Stores an {@link AssignmentRule} in Redis with the configured TTL.
     */
    public void putRule(AssignmentRuleKey key, AssignmentRule rule) {
        String cacheKey = RULE_PREFIX + key.name();
        try {
            redisTemplate.opsForValue().set(
                    cacheKey, rule, Duration.ofSeconds(redisConfig.getRuleTtlSeconds()));
            log.debug("Cache PUT rule key={} ttl={}s", key, redisConfig.getRuleTtlSeconds());
        } catch (Exception ex) {
            log.warn("Redis write failed for rule key={}: {}", key, ex.getMessage());
        }
    }

    /**
     * Evicts the cached entry for a specific assignment rule key.
     * Called by the write path in {@code AssignmentRuleServiceImpl} immediately
     * after the MySQL row is updated.
     */
    public void evictRule(AssignmentRuleKey key) {
        String cacheKey = RULE_PREFIX + key.name();
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.info("Cache EVICT rule key={} deleted={}", key, deleted);
        } catch (Exception ex) {
            log.warn("Redis evict failed for rule key={}: {}", key, ex.getMessage());
        }
    }

    // ── Scoring rule cache — single factor ────────────────────────────────────

    /**
     * Returns the cached {@link ScoringRuleConfig} for a single factor, or empty.
     */
    public Optional<ScoringRuleConfig> getScoringConfig(AssignmentScoreFactor factor) {
        String cacheKey = SCORING_PREFIX + factor.name();
        try {
            Object value = redisTemplate.opsForValue().get(cacheKey);
            if (value instanceof ScoringRuleConfig config) {
                log.debug("Cache HIT scoring factor={}", factor);
                return Optional.of(config);
            }
        } catch (Exception ex) {
            log.warn("Redis read failed for scoring factor={}: {} — falling back to MySQL",
                    factor, ex.getMessage());
        }
        log.debug("Cache MISS scoring factor={}", factor);
        return Optional.empty();
    }

    /**
     * Stores a single {@link ScoringRuleConfig} in Redis with the configured TTL.
     */
    public void putScoringConfig(AssignmentScoreFactor factor, ScoringRuleConfig config) {
        String cacheKey = SCORING_PREFIX + factor.name();
        try {
            redisTemplate.opsForValue().set(
                    cacheKey, config, Duration.ofSeconds(redisConfig.getScoringTtlSeconds()));
            log.debug("Cache PUT scoring factor={} ttl={}s",
                    factor, redisConfig.getScoringTtlSeconds());
        } catch (Exception ex) {
            log.warn("Redis write failed for scoring factor={}: {}", factor, ex.getMessage());
        }
    }

    /**
     * Evicts the cached entry for a specific scoring factor AND the all-enabled
     * list (since the list contains this factor's config).
     */
    public void evictScoringConfig(AssignmentScoreFactor factor) {
        String cacheKey = SCORING_PREFIX + factor.name();
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.info("Cache EVICT scoring factor={} deleted={}", factor, deleted);
        } catch (Exception ex) {
            log.warn("Redis evict failed for scoring factor={}: {}", factor, ex.getMessage());
        }
        // Also invalidate the all-enabled list — it contains this factor
        evictAllEnabledScoringConfigs();
    }

    // ── Scoring rule cache — all-enabled list ─────────────────────────────────

    /**
     * Returns the cached list of all enabled {@link ScoringRuleConfig} objects,
     * or empty if not present.
     *
     * The all-enabled list is loaded by the scoring engine on every assignment.
     * Caching it avoids N individual Redis lookups (one per factor).
     */
    @SuppressWarnings("unchecked")
    public Optional<List<ScoringRuleConfig>> getAllEnabledScoringConfigs() {
        try {
            Object value = redisTemplate.opsForValue().get(SCORING_ALL_ENABLED_KEY);
            if (value instanceof List<?> list && !list.isEmpty()
                    && list.get(0) instanceof ScoringRuleConfig) {
                log.debug("Cache HIT scoring:all-enabled ({} configs)", list.size());
                return Optional.of((List<ScoringRuleConfig>) list);
            }
        } catch (Exception ex) {
            log.warn("Redis read failed for scoring:all-enabled: {} — falling back to MySQL",
                    ex.getMessage());
        }
        log.debug("Cache MISS scoring:all-enabled");
        return Optional.empty();
    }

    /**
     * Stores the complete list of enabled scoring configs.
     */
    public void putAllEnabledScoringConfigs(List<ScoringRuleConfig> configs) {
        try {
            redisTemplate.opsForValue().set(
                    SCORING_ALL_ENABLED_KEY, configs,
                    Duration.ofSeconds(redisConfig.getScoringTtlSeconds()));
            log.debug("Cache PUT scoring:all-enabled ({} configs) ttl={}s",
                    configs.size(), redisConfig.getScoringTtlSeconds());
        } catch (Exception ex) {
            log.warn("Redis write failed for scoring:all-enabled: {}", ex.getMessage());
        }
    }

    /**
     * Evicts the all-enabled scoring config list.
     * Called whenever any scoring rule is updated (enabled/disabled/weight changed).
     */
    public void evictAllEnabledScoringConfigs() {
        try {
            Boolean deleted = redisTemplate.delete(SCORING_ALL_ENABLED_KEY);
            log.info("Cache EVICT scoring:all-enabled deleted={}", deleted);
        } catch (Exception ex) {
            log.warn("Redis evict failed for scoring:all-enabled: {}", ex.getMessage());
        }
    }
}
