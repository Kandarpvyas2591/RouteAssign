package com.routeassign.config;

import com.routeassign.domain.entity.AssignmentRule;
import com.routeassign.domain.entity.AssignmentScoringRule;
import com.routeassign.domain.enums.AssignmentRuleKey;
import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.domain.enums.ScoringMethod;
import com.routeassign.repository.AssignmentRuleRepository;
import com.routeassign.repository.AssignmentScoringRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds both the {@code assignment_rules} and {@code assignment_scoring_rules}
 * tables with initial values on every application startup.
 *
 * Strategy: INSERT-IF-ABSENT
 *   - Existing rows are never overwritten — admin changes made through the API
 *     are always preserved across restarts.
 *   - Only missing rows are inserted.
 *
 * To change a value after first deployment, use the admin APIs:
 *   PUT /api/v1/assignment-rules/{key}
 *   PUT /api/v1/scoring-rules/{factor}
 *
 * Adding a new assignment rule:
 *   1. New constant in {@link AssignmentRuleKey}.
 *   2. New {@link RuleSeedEntry} here.
 *   3. Validation branch in {@code AssignmentRuleServiceImpl.validateValue()}.
 *
 * Adding a new scoring factor:
 *   1. New constant in {@link AssignmentScoreFactor}.
 *   2. New {@link ScoringSeedEntry} here.
 *   3. A new {@code AssignmentScoringStrategy} implementation for the factor.
 *   4. Validation branch in {@code AssignmentScoringRuleServiceImpl.validateFields()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AssignmentRuleRepository         assignmentRuleRepository;
    private final AssignmentScoringRuleRepository  assignmentScoringRuleRepository;

    // ═══════════════════════════════════════════════════════════════════════
    // Assignment rules seed  (assignment_rules table)
    // ═══════════════════════════════════════════════════════════════════════

    private static final List<RuleSeedEntry> SEED_RULES = List.of(

        new RuleSeedEntry(
            AssignmentRuleKey.HOME_VENDOR_MAX_DISTANCE, "30", "DOUBLE",
            "If an assignment is placed after LATE_ASSIGNMENT_HOUR and the distance "
            + "from the delivery partner's home to the vendor exceeds this threshold (km), "
            + "work is deferred to the next working day. Must be > 0."
        ),

        new RuleSeedEntry(
            AssignmentRuleKey.SAME_VENDOR_MAX_DISTANCE, "10", "DOUBLE",
            "Maximum extra distance (km) a same-vendor delivery partner may travel to "
            + "a new customer location before the system stops reusing that partner "
            + "and runs the full selection algorithm instead. Must be > 0."
        ),

        new RuleSeedEntry(
            AssignmentRuleKey.WORKING_HOUR_START, "10", "INTEGER",
            "Start of the delivery working day (24-hour clock, inclusive). "
            + "E.g. 10 means 10:00 AM. Must be in [0, 23] and less than WORKING_HOUR_END."
        ),

        new RuleSeedEntry(
            AssignmentRuleKey.WORKING_HOUR_END, "20", "INTEGER",
            "End of the delivery working day (24-hour clock, exclusive). "
            + "E.g. 20 means 8:00 PM. Must be in [1, 24] and greater than WORKING_HOUR_START."
        ),

        new RuleSeedEntry(
            AssignmentRuleKey.LATE_ASSIGNMENT_HOUR, "17", "INTEGER",
            "Hour after which the HOME_VENDOR_MAX_DISTANCE check is applied (24-hour clock). "
            + "E.g. 17 means 5:00 PM. Must be within [WORKING_HOUR_START, WORKING_HOUR_END)."
        ),

        new RuleSeedEntry(
            AssignmentRuleKey.REST_DURATION_MINUTES, "30", "INTEGER",
            "Minutes a delivery partner rests at home between completing one delivery "
            + "and starting the next (used in the busy-partner ETA formula). Must be >= 0."
        ),

        new RuleSeedEntry(
            AssignmentRuleKey.ENABLE_BUSY_PARTNER_REUSE, "true", "BOOLEAN",
            "Controls whether the assignment engine considers partners who are currently "
            + "mid-delivery on a different vendor's order, provided they have sufficient "
            + "remaining capacity. Accepted values: true | false."
        ),

        new RuleSeedEntry(
            AssignmentRuleKey.ASSIGNMENT_ACCEPTANCE_TIMEOUT_MINUTES, "10", "INTEGER",
            "Minutes a delivery partner has to accept an assignment before it is automatically "
            + "expired and reassignment is triggered. Must be > 0. "
            + "Detected by AssignmentExpiryService on a scheduled basis."
        ),

        new RuleSeedEntry(
            AssignmentRuleKey.MAX_ASSIGNMENT_ATTEMPTS, "3", "INTEGER",
            "Maximum number of assignment attempts (including the initial one) before an order "
            + "is moved to WAITING_FOR_PARTNER and requires manual intervention. Must be >= 1."
        )
    );

    // ═══════════════════════════════════════════════════════════════════════
    // Scoring rules seed  (assignment_scoring_rules table)
    //
    // Weights: 40 / 20 / 15 / 15 / 10  (sum = 100; engine normalises)
    //
    // DISTANCE    INVERSE_LINEAR  min=0, max=50 km
    //   0 km → 100,  50+ km → 0
    //
    // CAPACITY    LINEAR          min=0, max=50 kg
    //   50 kg remaining → 100,  0 kg → 0
    //
    // RATING      NORMALIZED      min=1, max=5
    //   minValue=1 shifts the scale so a rating of 1 scores 0 and 5 scores 100.
    //   Without minValue the NORMALIZED formula would treat 1/5 = 20, which
    //   unfairly penalises the minimum rating relative to a 0-based scale.
    //
    // IDLE_TIME   LINEAR          min=0, max=10080 min (1 week)
    //   maxValue caps the scoring window; beyond 1 week idle time no longer
    //   increases the score, preventing long-dormant partners from dominating.
    //
    // SAME_VENDOR BINARY          positiveScore=100, negativeScore=0
    //   Same vendor → 100, different vendor → 0.
    //   Both values are configurable via the admin API.
    // ═══════════════════════════════════════════════════════════════════════

    private static final List<ScoringSeedEntry> SEED_SCORING_RULES = List.of(

        new ScoringSeedEntry(
            AssignmentScoreFactor.DISTANCE,
            40.0, ScoringMethod.INVERSE_LINEAR, 0.0, 50.0, null, null,
            "Total route distance (partner→vendor→customer) in km. "
            + "Lower distance scores higher. "
            + "minValue=0 (best case), maxValue=50 km (worst case within city range). "
            + "Partners beyond 50 km receive score 0 for this factor."
        ),

        new ScoringSeedEntry(
            AssignmentScoreFactor.CAPACITY,
            20.0, ScoringMethod.LINEAR, 0.0, 50.0, null, null,
            "Remaining carrying capacity of the partner (capacity − currentAssignedWeight) in kg. "
            + "Higher remaining capacity scores higher. "
            + "minValue=0, maxValue=50 kg (expected maximum partner capacity)."
        ),

        new ScoringSeedEntry(
            AssignmentScoreFactor.RATING,
            15.0, ScoringMethod.NORMALIZED, 1.0, 5.0, null, null,
            "Aggregate partner rating. "
            + "minValue=1 (lowest rating on the 1–5 scale), maxValue=5 (highest). "
            + "A rating of 1 maps to 0, a rating of 5 maps to 100. "
            + "Partners with no recorded rating are scored as 0 (treated as unrated)."
        ),

        new ScoringSeedEntry(
            AssignmentScoreFactor.IDLE_TIME,
            15.0, ScoringMethod.LINEAR, 0.0, 10080.0, null, null,
            "Minutes since the partner last completed a delivery. "
            + "Longer idle time scores higher (fairness — less-busy partners are preferred). "
            + "maxValue=10080 minutes (1 week) caps the scoring window so that dormant "
            + "partners do not dominate simply due to extended inactivity. "
            + "Partners who have never been assigned are treated as maximally idle."
        ),

        new ScoringSeedEntry(
            AssignmentScoreFactor.SAME_VENDOR,
            10.0, ScoringMethod.BINARY, null, null, 100.0, 0.0,
            "Whether the partner already has an active assignment at the same vendor. "
            + "positiveScore=100 (same vendor), negativeScore=0 (different vendor). "
            + "Both scores are configurable via the admin API — reducing positiveScore "
            + "makes same-vendor consolidation less dominant in the final ranking."
        )
    );

    // ═══════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAssignmentRules();
        seedScoringRules();
    }

    // ── Assignment rules ──────────────────────────────────────────────────────

    private void seedAssignmentRules() {
        int inserted = 0;
        for (RuleSeedEntry entry : SEED_RULES) {
            if (assignmentRuleRepository.findByRuleKey(entry.key()).isPresent()) {
                log.debug("Seed [assignment_rules]: key={} already present — skipping", entry.key());
                continue;
            }
            AssignmentRule rule = AssignmentRule.builder()
                    .ruleKey(entry.key())
                    .ruleValue(entry.defaultValue())
                    .valueType(entry.valueType())
                    .description(entry.description())
                    .isActive(true)
                    .build();
            assignmentRuleRepository.save(rule);
            log.info("Seed [assignment_rules]: inserted key={} value={}", entry.key(), entry.defaultValue());
            inserted++;
        }
        log.info("Seed [assignment_rules] complete — {} inserted, {} already present",
                inserted, SEED_RULES.size() - inserted);
    }

    // ── Scoring rules ─────────────────────────────────────────────────────────

    private void seedScoringRules() {
        int inserted = 0;
        for (ScoringSeedEntry entry : SEED_SCORING_RULES) {
            if (assignmentScoringRuleRepository.findByFactor(entry.factor()).isPresent()) {
                log.debug("Seed [assignment_scoring_rules]: factor={} already present — skipping",
                        entry.factor());
                continue;
            }
            AssignmentScoringRule rule = AssignmentScoringRule.builder()
                    .factor(entry.factor())
                    .weight(entry.weight())
                    .isEnabled(true)
                    .scoringMethod(entry.method())
                    .minValue(entry.minValue())
                    .maxValue(entry.maxValue())
                    .positiveScore(entry.positiveScore())
                    .negativeScore(entry.negativeScore())
                    .description(entry.description())
                    .build();
            assignmentScoringRuleRepository.save(rule);
            log.info("Seed [assignment_scoring_rules]: inserted factor={} weight={} method={}",
                    entry.factor(), entry.weight(), entry.method());
            inserted++;
        }
        log.info("Seed [assignment_scoring_rules] complete — {} inserted, {} already present",
                inserted, SEED_SCORING_RULES.size() - inserted);
    }

    // ── Seed record types ─────────────────────────────────────────────────────

    private record RuleSeedEntry(
            AssignmentRuleKey key,
            String            defaultValue,
            String            valueType,
            String            description
    ) {}

    private record ScoringSeedEntry(
            AssignmentScoreFactor factor,
            double                weight,
            ScoringMethod         method,
            Double                minValue,
            Double                maxValue,
            Double                positiveScore,
            Double                negativeScore,
            String                description
    ) {}
}
