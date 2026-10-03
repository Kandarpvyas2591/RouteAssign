package com.routeassign.controller;

import com.routeassign.domain.entity.AssignmentScoringRule;
import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.dto.request.UpdateScoringRuleRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.ScoringRuleResponse;
import com.routeassign.exception.BadRequestException;
import com.routeassign.service.AssignmentScoringRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * Admin API for reading and updating assignment scoring rules.
 *
 * <pre>
 * GET /api/v1/scoring-rules            — list all scoring rules (authenticated)
 * GET /api/v1/scoring-rules/{factor}   — get a single rule by factor (authenticated)
 * PUT /api/v1/scoring-rules/{factor}   — update a rule's configuration (ADMIN only)
 * </pre>
 *
 * The {factor} path variable must match one of the {@link AssignmentScoreFactor}
 * enum names (case-insensitive).
 *
 * Authorization:
 *   GET endpoints — open to any authenticated user (enforcement via SecurityConfig).
 *   PUT endpoint  — ADMIN role required, enforced here via @PreAuthorize AND in
 *                   SecurityConfig so that the restriction has two layers of defence.
 *
 * NOTE: @PreAuthorize requires @EnableMethodSecurity on a @Configuration class.
 * That annotation is added to SecurityConfig as part of Phase 2.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scoring-rules")
@RequiredArgsConstructor
public class AssignmentScoringRuleController {

    private final AssignmentScoringRuleService assignmentScoringRuleService;

    // ── GET /api/v1/scoring-rules ─────────────────────────────────────────────

    /**
     * Returns all scoring rules (enabled and disabled) for admin inspection.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ScoringRuleResponse>>> getAllRules() {
        List<ScoringRuleResponse> rules = assignmentScoringRuleService.getAllRules()
                .stream()
                .map(ScoringRuleResponse::from)
                .toList();

        log.debug("Fetched all scoring rules ({} total)", rules.size());
        return ResponseEntity.ok(ApiResponse.success("Scoring rules retrieved successfully", rules));
    }

    // ── GET /api/v1/scoring-rules/{factor} ────────────────────────────────────

    /**
     * Returns a single scoring rule by factor name (e.g. {@code DISTANCE}).
     */
    @GetMapping("/{factor}")
    public ResponseEntity<ApiResponse<ScoringRuleResponse>> getRuleByFactor(
            @PathVariable String factor) {

        AssignmentScoreFactor scoreFactor = resolveFactor(factor);
        AssignmentScoringRule rule = assignmentScoringRuleService.getRuleByFactor(scoreFactor);

        log.debug("Fetched scoring rule factor={}", scoreFactor);
        return ResponseEntity.ok(ApiResponse.success(ScoringRuleResponse.from(rule)));
    }

    // ── PUT /api/v1/scoring-rules/{factor} ────────────────────────────────────

    /**
     * Updates configurable fields of a scoring rule.
     *
     * All request body fields are optional — only supplied (non-null) fields are
     * applied. At least one field must be non-null.
     *
     * Example:
     * <pre>
     * PUT /api/v1/scoring-rules/DISTANCE
     * { "weight": 50, "maxValue": 60.0 }
     * </pre>
     *
     * Restricted to ADMIN role — enforced here AND in SecurityConfig (defence in depth).
     */
    @PutMapping("/{factor}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ScoringRuleResponse>> updateRule(
            @PathVariable String factor,
            @Valid @RequestBody UpdateScoringRuleRequest request) {

        // Require at least one field to be set — otherwise the PUT has no effect
        if (request.getWeight()         == null
                && request.getIsEnabled()    == null
                && request.getScoringMethod() == null
                && request.getMinValue()     == null
                && request.getMaxValue()     == null
                && request.getPositiveScore() == null
                && request.getNegativeScore() == null) {
            throw new BadRequestException(
                    "At least one field must be provided: weight, isEnabled, "
                    + "scoringMethod, minValue, maxValue, positiveScore, negativeScore.");
        }

        AssignmentScoreFactor scoreFactor = resolveFactor(factor);

        // TODO: replace "admin" with SecurityContextHolder principal once JWT is wired
        String updatedBy = "admin";

        AssignmentScoringRule updated = assignmentScoringRuleService.updateRule(
                scoreFactor,
                request.getWeight(),
                request.getIsEnabled(),
                request.getScoringMethod(),
                request.getMinValue(),
                request.getMaxValue(),
                request.getPositiveScore(),
                request.getNegativeScore(),
                updatedBy
        );

        log.info("Admin updated scoring rule factor={} weight={} enabled={} method={}",
                scoreFactor,
                request.getWeight()        != null ? request.getWeight()        : "(unchanged)",
                request.getIsEnabled()     != null ? request.getIsEnabled()     : "(unchanged)",
                request.getScoringMethod() != null ? request.getScoringMethod() : "(unchanged)");

        return ResponseEntity.ok(
                ApiResponse.success("Scoring rule updated successfully", ScoringRuleResponse.from(updated)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private AssignmentScoreFactor resolveFactor(String raw) {
        try {
            return AssignmentScoreFactor.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Unknown scoring factor: '" + raw + "'. "
                    + "Valid factors: " + Arrays.toString(AssignmentScoreFactor.values()));
        }
    }
}
