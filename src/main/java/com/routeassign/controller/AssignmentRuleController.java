package com.routeassign.controller;

import com.routeassign.domain.entity.AssignmentRule;
import com.routeassign.domain.enums.AssignmentRuleKey;
import com.routeassign.dto.request.UpdateRuleRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.AssignmentRuleResponse;
import com.routeassign.service.AssignmentRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin API for reading and updating assignment business rules.
 *
 * <pre>
 * GET  /api/v1/assignment-rules          — list all active rules
 * GET  /api/v1/assignment-rules/{key}    — get a single rule by key
 * PUT  /api/v1/assignment-rules/{key}    — update a rule's value
 * </pre>
 *
 * The {key} path variable must exactly match one of the {@link AssignmentRuleKey}
 * enum names (case-insensitive — converted to upper-case before lookup).
 *
 * NOTE: Role-based access control (ADMIN only) will be applied once the JWT
 * filter is wired into SecurityConfig in a later phase.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assignment-rules")
@RequiredArgsConstructor
public class AssignmentRuleController {

    private final AssignmentRuleService assignmentRuleService;

    // ── GET /api/v1/assignment-rules ──────────────────────────────────────────

    /**
     * Returns all currently active assignment rules.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AssignmentRuleResponse>>> getAllRules() {
        List<AssignmentRuleResponse> rules = assignmentRuleService.getAllActiveRules()
                .stream()
                .map(AssignmentRuleResponse::from)
                .toList();

        log.debug("Admin fetched all assignment rules ({} active)", rules.size());
        return ResponseEntity.ok(ApiResponse.success("Assignment rules retrieved successfully", rules));
    }

    // ── GET /api/v1/assignment-rules/{key} ────────────────────────────────────

    /**
     * Returns a single rule by its key name (e.g. {@code HOME_VENDOR_MAX_DISTANCE}).
     *
     * Returns the rule regardless of its active status so the admin can inspect
     * and choose to re-activate it.
     */
    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<AssignmentRuleResponse>> getRuleByKey(
            @PathVariable String key) {

        AssignmentRuleKey ruleKey = resolveKey(key);
        AssignmentRule rule = assignmentRuleService.getRuleByKey(ruleKey);

        log.debug("Admin fetched rule key={}", ruleKey);
        return ResponseEntity.ok(ApiResponse.success(AssignmentRuleResponse.from(rule)));
    }

    // ── PUT /api/v1/assignment-rules/{key} ────────────────────────────────────

    /**
     * Updates the value of an existing rule.
     *
     * Request body: {@code { "value": "40" }}
     *
     * The new value is validated against the rule's declared type and domain
     * constraints before saving. Returns the updated rule on success.
     *
     * The {@code updatedBy} field is currently populated with a placeholder
     * ("admin") until JWT authentication is wired in, at which point it will
     * be replaced with the authenticated user's identifier from the security
     * context.
     */
    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<AssignmentRuleResponse>> updateRule(
            @PathVariable String key,
            @Valid @RequestBody UpdateRuleRequest request) {

        AssignmentRuleKey ruleKey = resolveKey(key);

        // TODO: replace "admin" with SecurityContextHolder principal once JWT is wired
        String updatedBy = "admin";

        AssignmentRule updated = assignmentRuleService.updateRule(ruleKey, request.getValue(), updatedBy);

        log.info("Admin updated rule key={} newValue={}", ruleKey, request.getValue());
        return ResponseEntity.ok(
                ApiResponse.success("Rule updated successfully", AssignmentRuleResponse.from(updated)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Converts the raw path variable string to an {@link AssignmentRuleKey} enum constant.
     * Accepts both upper-case and lower-case input.
     *
     * @throws com.routeassign.exception.BadRequestException if the key is not recognised.
     */
    private AssignmentRuleKey resolveKey(String raw) {
        try {
            return AssignmentRuleKey.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.routeassign.exception.BadRequestException(
                    "Unknown assignment rule key: '" + raw + "'. "
                    + "Valid keys: " + java.util.Arrays.toString(AssignmentRuleKey.values()));
        }
    }
}
