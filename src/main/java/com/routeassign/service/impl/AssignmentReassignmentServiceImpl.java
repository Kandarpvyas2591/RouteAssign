package com.routeassign.service.impl;

import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.UserDetails;
import com.routeassign.domain.enums.AssignmentFailureReason;
import com.routeassign.domain.enums.AssignmentRuleKey;
import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.exception.NoEligiblePartnerException;
import com.routeassign.repository.DeliveryAssignmentRepository;
import com.routeassign.repository.UserDetailsRepository;
import com.routeassign.service.AssignmentReassignmentService;
import com.routeassign.service.AssignmentRuleService;
import com.routeassign.service.DeliveryAssignmentService;
import com.routeassign.service.algorithm.AssignmentContext;
import com.routeassign.service.algorithm.PartnerSelectionAlgorithmService;
import com.routeassign.service.algorithm.PartnerSelectionAlgorithmService.PartnerSelectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements {@link AssignmentReassignmentService}.
 *
 * Reassignment flow
 * ─────────────────
 * 1. Collect all previous partner IDs that failed for this order.
 * 2. Check whether MAX_ASSIGNMENT_ATTEMPTS has been reached.
 *    If yes → set the failed assignment to WAITING_FOR_PARTNER and return null.
 * 3. Build the next {@link AssignmentContext} with exclusions.
 * 4. Run the standard eligibility + scoring via {@link PartnerSelectionAlgorithmService}.
 * 5. If a partner is found → delegate to {@link DeliveryAssignmentService#assign(Order, AssignmentContext)}
 *    to persist the new DeliveryAssignment (attempt N+1).
 * 6. If no partner found → set WAITING_FOR_PARTNER and return null.
 *
 * The failed assignment row is never deleted. Its status is set to REASSIGNING
 * by the caller ({@code AssignmentLifecycleService}) before this method is invoked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentReassignmentServiceImpl implements AssignmentReassignmentService {

    private final DeliveryAssignmentRepository     deliveryAssignmentRepository;
    private final UserDetailsRepository            userDetailsRepository;
    private final AssignmentRuleService            assignmentRuleService;
    private final PartnerSelectionAlgorithmService partnerSelectionAlgorithmService;

    /**
     * Lazy to break the circular dependency:
     * AssignmentReassignmentService → DeliveryAssignmentService → DeliveryAssignmentServiceImpl
     * DeliveryAssignmentServiceImpl → AssignmentReassignmentService (indirectly via lifecycle)
     */
    @Lazy
    private final DeliveryAssignmentService deliveryAssignmentService;

    @Override
    @Transactional
    public DeliveryAssignmentResponse reassign(DeliveryAssignment failedAssignment,
                                               AssignmentFailureReason failureReason) {
        Order order = failedAssignment.getOrder();
        Long  orderId = order.getOrderId();
        Long  failedPartnerId = failedAssignment.getDeliveryPartner().getId();

        log.info("Reassigning orderId={} failedPartnerId={} reason={}",
                orderId, failedPartnerId, failureReason);

        // ── Step 1: collect all previously failed partner IDs for this order ──
        Set<Long> excluded = collectExcludedPartnerIds(orderId);

        // ── Step 2: check MAX_ASSIGNMENT_ATTEMPTS ─────────────────────────────
        int maxAttempts = assignmentRuleService
                .getIntegerRule(AssignmentRuleKey.MAX_ASSIGNMENT_ATTEMPTS);

        long totalAttempts = deliveryAssignmentRepository.countByOrderId(orderId);

        if (totalAttempts >= maxAttempts) {
            log.warn("orderId={} reached maxAttempts={} — setting WAITING_FOR_PARTNER",
                    orderId, maxAttempts);
            setWaitingForPartner(failedAssignment, AssignmentFailureReason.MAX_ATTEMPTS_REACHED);
            return null;
        }

        // ── Step 3: build next AssignmentContext ──────────────────────────────
        int nextAttempt = failedAssignment.getAttemptNumber() + 1;
        AssignmentContext context = new AssignmentContext(order, excluded, nextAttempt, failureReason);

        log.debug("Next attempt context: {}", context);

        // ── Step 4 + 5: run selection, persist new assignment ─────────────────
        try {
            return deliveryAssignmentService.assign(order, context);
        } catch (NoEligiblePartnerException ex) {
            // ── Step 6: no eligible partner found ─────────────────────────────
            log.warn("No eligible partner for reassignment orderId={} attempt={}: {}",
                    orderId, nextAttempt, ex.getMessage());
            setWaitingForPartner(failedAssignment, AssignmentFailureReason.NO_ELIGIBLE_PARTNER);
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the set of all partner IDs that have already been tried for this
     * order and ended in a non-delivery outcome (REJECTED, EXPIRED, DELIVERY_FAILED,
     * REASSIGNING). These are excluded from the next attempt.
     */
    private Set<Long> collectExcludedPartnerIds(Long orderId) {
        List<DeliveryAssignment> all = deliveryAssignmentRepository.findAllByOrderId(orderId);

        Set<Long> excluded = new HashSet<>();
        for (DeliveryAssignment da : all) {
            if (isFailedStatus(da.getDeliveryStatus())) {
                excluded.add(da.getDeliveryPartner().getId());
            }
        }
        return excluded;
    }

    private boolean isFailedStatus(DeliveryStatus status) {
        return status == DeliveryStatus.REJECTED
                || status == DeliveryStatus.EXPIRED
                || status == DeliveryStatus.DELIVERY_FAILED
                || status == DeliveryStatus.REASSIGNING
                || status == DeliveryStatus.CANCELLED;
    }

    /**
     * Transitions the failed assignment to WAITING_FOR_PARTNER and persists it.
     * No further reassignment will be attempted automatically.
     */
    private void setWaitingForPartner(DeliveryAssignment da, AssignmentFailureReason reason) {
        da.setDeliveryStatus(DeliveryStatus.WAITING_FOR_PARTNER);
        da.setFailureReason(reason);
        deliveryAssignmentRepository.save(da);

        log.info("Assignment id={} orderId={} moved to WAITING_FOR_PARTNER reason={}",
                da.getId(), da.getOrder().getOrderId(), reason);
    }
}
