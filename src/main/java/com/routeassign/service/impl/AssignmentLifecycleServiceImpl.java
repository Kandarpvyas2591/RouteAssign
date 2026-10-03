package com.routeassign.service.impl;

import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.domain.enums.AssignmentFailureReason;
import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.domain.enums.HistoryStatus;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.exception.InvalidStatusTransitionException;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.DeliveryAssignmentRepository;
import com.routeassign.service.AssignmentLifecycleService;
import com.routeassign.service.AssignmentReassignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Implements the {@link AssignmentLifecycleService} state machine.
 *
 * Each lifecycle method follows the same four-step pattern:
 *   1. Load the assignment.
 *   2. Validate the transition is legal (throws on invalid).
 *   3. Apply status + timestamps.
 *   4. Persist, trigger side-effects, return response.
 *
 * Side-effects on terminal transitions (history records, partner weight/availability)
 * are handled by {@link AssignmentClosingHelper} — this class owns zero of that logic.
 *
 * Reassignment triggers (reject / expire / delivery-failed) are delegated to
 * {@link AssignmentReassignmentService} — this class owns zero selection logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentLifecycleServiceImpl implements AssignmentLifecycleService {

    private final DeliveryAssignmentRepository  deliveryAssignmentRepository;
    private final AssignmentClosingHelper        closingHelper;
    private final AssignmentReassignmentService  reassignmentService;

    // ── Status groups ─────────────────────────────────────────────────────────

    /**
     * Statuses from which no further lifecycle transitions are permitted.
     * An assignment that has already closed cannot be re-opened.
     */
    static final Set<DeliveryStatus> CLOSING_STATUSES = Set.of(
            DeliveryStatus.DELIVERED,
            DeliveryStatus.CANCELLED,
            DeliveryStatus.DELIVERY_FAILED,
            DeliveryStatus.REJECTED,
            DeliveryStatus.EXPIRED,
            DeliveryStatus.WAITING_FOR_PARTNER
    );

    // ── Valid transition map ──────────────────────────────────────────────────

    private static boolean isValidTransition(DeliveryStatus from, DeliveryStatus to) {
        return switch (from) {
            case ASSIGNED  -> to == DeliveryStatus.ACCEPTED
                           || to == DeliveryStatus.REJECTED
                           || to == DeliveryStatus.EXPIRED
                           || to == DeliveryStatus.CANCELLED;
            case ACCEPTED  -> to == DeliveryStatus.PICKED_UP
                           || to == DeliveryStatus.CANCELLED;
            case PICKED_UP -> to == DeliveryStatus.IN_TRANSIT;
            case IN_TRANSIT -> to == DeliveryStatus.DELIVERED
                            || to == DeliveryStatus.DELIVERY_FAILED;
            default        -> false;
        };
    }

    // ── Lifecycle transitions ─────────────────────────────────────────────────

    @Override
    @Transactional
    public DeliveryAssignmentResponse acceptAssignment(Long assignmentId) {
        DeliveryAssignment da = load(assignmentId);
        transition(da, DeliveryStatus.ACCEPTED);
        da.setAcceptedAt(LocalDateTime.now());
        da = deliveryAssignmentRepository.save(da);
        log.info("Assignment {} ACCEPTED", assignmentId);
        return AssignmentResponseMapper.toResponse(da);
    }

    @Override
    @Transactional
    public DeliveryAssignmentResponse rejectAssignment(Long assignmentId, String reason) {
        DeliveryAssignment da = load(assignmentId);
        transition(da, DeliveryStatus.REJECTED);
        da.setFailureReason(AssignmentFailureReason.PARTNER_REJECTED);
        da = deliveryAssignmentRepository.save(da);
        log.info("Assignment {} REJECTED reason='{}'", assignmentId, reason);

        closingHelper.closeAttempt(da, HistoryStatus.CANCELLED);
        markAsReassigning(da);
        reassignmentService.reassign(da, AssignmentFailureReason.PARTNER_REJECTED);
        return AssignmentResponseMapper.toResponse(da);
    }

    @Override
    @Transactional
    public DeliveryAssignmentResponse expireAssignment(Long assignmentId) {
        DeliveryAssignment da = load(assignmentId);
        transition(da, DeliveryStatus.EXPIRED);
        da.setFailureReason(AssignmentFailureReason.PARTNER_TIMEOUT);
        da = deliveryAssignmentRepository.save(da);
        log.info("Assignment {} EXPIRED (timeout)", assignmentId);

        closingHelper.closeAttempt(da, HistoryStatus.CANCELLED);
        markAsReassigning(da);
        reassignmentService.reassign(da, AssignmentFailureReason.PARTNER_TIMEOUT);
        return AssignmentResponseMapper.toResponse(da);
    }

    @Override
    @Transactional
    public DeliveryAssignmentResponse cancelAssignment(Long assignmentId, String reason) {
        DeliveryAssignment da = load(assignmentId);
        transition(da, DeliveryStatus.CANCELLED);
        da.setFailureReason(AssignmentFailureReason.PARTNER_CANCELLED);
        da.setCompletedAt(LocalDateTime.now());
        da = deliveryAssignmentRepository.save(da);
        log.info("Assignment {} CANCELLED reason='{}'", assignmentId, reason);

        closingHelper.closeAttempt(da, HistoryStatus.CANCELLED);
        return AssignmentResponseMapper.toResponse(da);
    }

    @Override
    @Transactional
    public DeliveryAssignmentResponse markPickedUp(Long assignmentId) {
        DeliveryAssignment da = load(assignmentId);
        transition(da, DeliveryStatus.PICKED_UP);
        da = deliveryAssignmentRepository.save(da);
        log.info("Assignment {} PICKED_UP", assignmentId);
        return AssignmentResponseMapper.toResponse(da);
    }

    @Override
    @Transactional
    public DeliveryAssignmentResponse markInTransit(Long assignmentId) {
        DeliveryAssignment da = load(assignmentId);
        transition(da, DeliveryStatus.IN_TRANSIT);
        da = deliveryAssignmentRepository.save(da);
        log.info("Assignment {} IN_TRANSIT", assignmentId);
        return AssignmentResponseMapper.toResponse(da);
    }

    @Override
    @Transactional
    public DeliveryAssignmentResponse completeDelivery(Long assignmentId) {
        DeliveryAssignment da = load(assignmentId);
        transition(da, DeliveryStatus.DELIVERED);
        da.setCompletedAt(LocalDateTime.now());
        da = deliveryAssignmentRepository.save(da);
        log.info("Assignment {} DELIVERED", assignmentId);

        closingHelper.closeAttempt(da, HistoryStatus.COMPLETED);
        return AssignmentResponseMapper.toResponse(da);
    }

    @Override
    @Transactional
    public DeliveryAssignmentResponse markDeliveryFailed(Long assignmentId, String reason) {
        DeliveryAssignment da = load(assignmentId);
        transition(da, DeliveryStatus.DELIVERY_FAILED);
        da.setFailureReason(AssignmentFailureReason.DELIVERY_FAILED);
        da.setCompletedAt(LocalDateTime.now());
        da = deliveryAssignmentRepository.save(da);
        log.info("Assignment {} DELIVERY_FAILED reason='{}'", assignmentId, reason);

        closingHelper.closeAttempt(da, HistoryStatus.FAILED);
        markAsReassigning(da);
        reassignmentService.reassign(da, AssignmentFailureReason.DELIVERY_FAILED);
        return AssignmentResponseMapper.toResponse(da);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private DeliveryAssignment load(Long id) {
        return deliveryAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DeliveryAssignment", "id", id));
    }

    /**
     * Validates the requested transition and applies the new status.
     * Throws {@link InvalidStatusTransitionException} on any illegal move.
     */
    private void transition(DeliveryAssignment da, DeliveryStatus next) {
        DeliveryStatus current = da.getDeliveryStatus();
        if (CLOSING_STATUSES.contains(current)) {
            throw new InvalidStatusTransitionException("DeliveryAssignment", current, next);
        }
        if (!isValidTransition(current, next)) {
            throw new InvalidStatusTransitionException("DeliveryAssignment", current, next);
        }
        da.setDeliveryStatus(next);
    }

    /**
     * Transitions the assignment to REASSIGNING so that subsequent history
     * queries can identify that a new attempt was created.
     */
    private void markAsReassigning(DeliveryAssignment da) {
        da.setDeliveryStatus(DeliveryStatus.REASSIGNING);
        deliveryAssignmentRepository.save(da);
    }
}
