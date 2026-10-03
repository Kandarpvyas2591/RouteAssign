package com.routeassign.service.impl;

import com.routeassign.domain.entity.*;
import com.routeassign.domain.enums.AssignmentFailureReason;
import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.domain.enums.HistoryStatus;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.dto.response.ReassignResponse;
import com.routeassign.exception.AssignmentAttemptException;
import com.routeassign.exception.BadRequestException;
import com.routeassign.exception.InvalidStatusTransitionException;
import com.routeassign.exception.NoEligiblePartnerException;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.*;
import com.routeassign.service.DeliveryAssignmentService;
import com.routeassign.service.algorithm.AssignmentContext;
import com.routeassign.service.algorithm.DistanceAlgorithmService;
import com.routeassign.service.algorithm.PartnerSelectionAlgorithmService;
import com.routeassign.service.algorithm.PartnerSelectionAlgorithmService.ScoredCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the full assignment lifecycle from the service layer.
 *
 * Concurrency safety (Phase 6):
 *   - Idempotency guard prevents duplicate assignments for the same order.
 *   - Pessimistic row lock on the selected partner prevents two concurrent
 *     requests from simultaneously assigning the same partner beyond capacity.
 *   - Lock-then-recheck: after acquiring the lock, eligibility is re-validated
 *     against the freshly-read partner state.
 *
 * Side-effects on terminal transitions (history, partner weight, availability)
 * are handled exclusively by {@link AssignmentClosingHelper}.
 *
 * Response mapping is handled exclusively by {@link AssignmentResponseMapper}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAssignmentServiceImpl implements DeliveryAssignmentService {

    private final DeliveryAssignmentRepository     deliveryAssignmentRepository;
    private final UserDetailsRepository            userDetailsRepository;
    private final PartnerSelectionAlgorithmService partnerSelectionAlgorithmService;
    private final DistanceAlgorithmService         distanceAlgorithmService;
    private final AssignmentClosingHelper          closingHelper;

    /**
     * Terminal statuses — no further lifecycle transitions are possible.
     * Aligned with {@link AssignmentLifecycleServiceImpl#CLOSING_STATUSES}.
     */
    private static final Set<DeliveryStatus> TERMINAL = Set.of(
            DeliveryStatus.DELIVERED,
            DeliveryStatus.CANCELLED,
            DeliveryStatus.DELIVERY_FAILED,
            DeliveryStatus.REJECTED,
            DeliveryStatus.EXPIRED,
            DeliveryStatus.WAITING_FOR_PARTNER,
            DeliveryStatus.FAILED   // legacy
    );

    // ── assign — initial attempt ──────────────────────────────────────────────

    @Override
    @Transactional
    public DeliveryAssignmentResponse assign(Order order) {
        return assign(order, new AssignmentContext(order));
    }

    // ── assign — context-aware (used by reassignment service) ─────────────────

    /**
     * Concurrency-safe assignment.
     *
     * 1. Idempotency guard.
     * 2. Run full selection algorithm (eligibility → scoring → ranking). No locks yet.
     * 3. Lock-then-recheck loop over ranked candidates:
     *    a. Acquire PESSIMISTIC_WRITE on candidate's partner row.
     *    b. Re-check eligibility on the freshly-read state.
     *    c. If still eligible → persist assignment, update weight, save audit trail.
     *    d. If ineligible → throw {@link AssignmentAttemptException}, try next candidate.
     * 4. If all candidates fail the recheck → throw {@link NoEligiblePartnerException}.
     */
    @Override
    @Transactional
    public DeliveryAssignmentResponse assign(Order order, AssignmentContext context) {

        // Step 1: idempotency
        var existing = deliveryAssignmentRepository
                .findActiveAssignmentByOrderId(order.getOrderId());
        if (existing.isPresent()) {
            log.info("Idempotency: orderId={} already has active assignmentId={}",
                    order.getOrderId(), existing.get().getId());
            return AssignmentResponseMapper.toResponse(existing.get());
        }

        // Step 2: selection algorithm (no locks held during scoring)
        PartnerSelectionAlgorithmService.PartnerSelectionResult result =
                partnerSelectionAlgorithmService.selectPartnerWithEta(context)
                        .orElseThrow(NoEligiblePartnerException::new);

        List<ScoredCandidate> ranked = new ArrayList<>(result.scoredCandidates());
        ranked.sort((a, b) -> Double.compare(
                b.scoringResult().getFinalScore(),
                a.scoringResult().getFinalScore()));

        // Step 3: lock-then-recheck
        for (ScoredCandidate sc : ranked) {
            try {
                return attemptAssignment(order, context, sc, result);
            } catch (AssignmentAttemptException ex) {
                log.warn("Partner {} failed post-lock recheck ({}), trying next",
                        ex.getPartnerId(), ex.getRejectReason());
            }
        }

        log.error("All {} candidates failed post-lock recheck for orderId={}",
                ranked.size(), order.getOrderId());
        throw new NoEligiblePartnerException(
                "No partner could be assigned after " + ranked.size()
                + " attempt(s) — all candidates lost capacity to concurrent requests.");
    }

    /**
     * Critical section: acquire a pessimistic write lock on the partner row,
     * re-validate eligibility, and — if still eligible — persist everything.
     *
     * The lock is released when the surrounding {@code @Transactional} commits.
     * Only one partner row is ever locked per call; the entire partner table is
     * never locked.
     */
    private DeliveryAssignmentResponse attemptAssignment(
            Order                                                    order,
            AssignmentContext                                         context,
            ScoredCandidate                                          sc,
            PartnerSelectionAlgorithmService.PartnerSelectionResult  result) {

        Long candidateId = sc.candidate().getPartnerId();

        UserDetails locked = userDetailsRepository
                .findByIdWithPessimisticLock(candidateId)
                .orElseThrow(() -> new AssignmentAttemptException(
                        candidateId, "partner no longer exists"));

        if (!Boolean.TRUE.equals(locked.getIsActive())) {
            throw new AssignmentAttemptException(candidateId, "partner is no longer active");
        }
        double freshRemaining = remainingCapacity(locked);
        if (freshRemaining < order.getTotalWeight()) {
            throw new AssignmentAttemptException(candidateId,
                    "insufficient capacity after lock: remaining=" + freshRemaining
                    + " required=" + order.getTotalWeight());
        }

        boolean       isTop      = candidateId.equals(result.partner().getId());
        LocalDateTime eta        = isTop ? result.eta()           : sc.candidate().getEta();
        LocalDateTime workStart  = isTop ? result.workStartTime() : sc.candidate().getWorkStartTime();
        boolean       isBusy     = sc.candidate().isBusy();
        VendorDetails vendor     = order.getVendor();

        double distP2V = distanceAlgorithmService.calculateDistance(
                locked.getLatitude(), locked.getLongitude(),
                vendor.getLatitude(), vendor.getLongitude());
        double distV2C = distanceAlgorithmService.calculateDistance(
                vendor.getLatitude(), vendor.getLongitude(),
                order.getDeliveryLocationLatitude(), order.getDeliveryLocationLongitude());

        DeliveryAssignment assignment = DeliveryAssignment.builder()
                .order(order)
                .deliveryPartner(locked)
                .vendor(vendor)
                .attemptNumber(context.getAttemptNumber())
                .expectedDeliveryTime(eta)
                .deliveryStatus(DeliveryStatus.ASSIGNED)
                .distancePartnerToVendor(distP2V)
                .distanceVendorToCustomer(distV2C)
                .build();

        assignment = deliveryAssignmentRepository.save(assignment);

        // Update partner running weight (isAvailable stays false for busy partners)
        locked.setCurrentAssignedWeight(
                (locked.getCurrentAssignedWeight() != null
                        ? locked.getCurrentAssignedWeight() : 0.0)
                        + order.getTotalWeight());
        userDetailsRepository.save(locked);

        // Persist scoring audit trail
        partnerSelectionAlgorithmService.saveDecision(
                assignment,
                isTop ? result.scoringResult() : sc.scoringResult(),
                result.weightSnapshot(),
                result.allEligibility(),
                result.scoredCandidates(),
                locked.getId());

        log.info("Assigned orderId={} attempt={} partnerId={} isBusy={} ETA={} dist={:.2f}km",
                order.getOrderId(), context.getAttemptNumber(), locked.getId(),
                isBusy, eta, distP2V + distV2C);

        return AssignmentResponseMapper.toResponse(assignment, isBusy, workStart);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public DeliveryAssignmentResponse getById(Long id) {
        return AssignmentResponseMapper.toResponse(findById(id));
    }

    @Override
    public DeliveryAssignmentResponse getByOrderId(Long orderId) {
        DeliveryAssignment da = deliveryAssignmentRepository
                .findActiveAssignmentByOrderId(orderId)
                .or(() -> deliveryAssignmentRepository.findAllByOrderId(orderId)
                        .stream().findFirst())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DeliveryAssignment", "orderId", orderId));
        return AssignmentResponseMapper.toResponse(da);
    }

    @Override
    public List<DeliveryAssignmentResponse> getByPartnerId(Long partnerId) {
        return deliveryAssignmentRepository.findAllByDeliveryPartner_Id(partnerId)
                .stream().map(AssignmentResponseMapper::toResponse).toList();
    }

    @Override
    public List<DeliveryAssignmentResponse> getByVendorId(Long vendorId) {
        return deliveryAssignmentRepository.findAllByVendor_Id(vendorId)
                .stream().map(AssignmentResponseMapper::toResponse).toList();
    }

    @Override
    public List<DeliveryAssignmentResponse> getByStatus(DeliveryStatus status) {
        List<DeliveryAssignment> all = (status == null)
                ? deliveryAssignmentRepository.findAll()
                : deliveryAssignmentRepository.findAllByDeliveryStatus(status);
        return all.stream().map(AssignmentResponseMapper::toResponse).toList();
    }

    // ── Status update (admin override) ───────────────────────────────────────

    @Override
    @Transactional
    public DeliveryAssignmentResponse updateStatus(Long id, DeliveryStatus newStatus) {
        DeliveryAssignment da = findById(id);
        validateStatusTransition(da.getDeliveryStatus(), newStatus);

        da.setDeliveryStatus(newStatus);
        if (newStatus == DeliveryStatus.ACCEPTED) da.setAcceptedAt(LocalDateTime.now());
        if (TERMINAL.contains(newStatus))          da.setCompletedAt(LocalDateTime.now());

        da = deliveryAssignmentRepository.save(da);

        if (TERMINAL.contains(newStatus)) {
            HistoryStatus h = switch (newStatus) {
                case DELIVERED                      -> HistoryStatus.COMPLETED;
                case CANCELLED, REJECTED, EXPIRED   -> HistoryStatus.CANCELLED;
                case DELIVERY_FAILED, FAILED        -> HistoryStatus.FAILED;
                default                             -> HistoryStatus.COMPLETED;
            };
            closingHelper.closeAttempt(da, h);
        }

        log.info("Assignment id={} status → {}", id, newStatus);
        return AssignmentResponseMapper.toResponse(da);
    }

    // ── Status transition guard ───────────────────────────────────────────────

    private void validateStatusTransition(DeliveryStatus current, DeliveryStatus next) {
        if (TERMINAL.contains(current)) {
            throw new InvalidStatusTransitionException("DeliveryAssignment", current, next);
        }
        // These are always allowed from any non-terminal state
        if (next == DeliveryStatus.CANCELLED
                || next == DeliveryStatus.REJECTED
                || next == DeliveryStatus.EXPIRED
                || next == DeliveryStatus.DELIVERY_FAILED) {
            return;
        }

        boolean valid = switch (current) {
            case ASSIGNED    -> next == DeliveryStatus.ACCEPTED;
            case ACCEPTED    -> next == DeliveryStatus.PICKED_UP;
            case PICKED_UP   -> next == DeliveryStatus.IN_TRANSIT;
            case IN_TRANSIT  -> next == DeliveryStatus.DELIVERED;
            // Legacy backward compatibility
            case EN_ROUTE_TO_VENDOR -> next == DeliveryStatus.COLLECTED
                                    || next == DeliveryStatus.PICKED_UP;
            case COLLECTED          -> next == DeliveryStatus.IN_TRANSIT;
            default -> false;
        };

        if (!valid) {
            throw new InvalidStatusTransitionException("DeliveryAssignment", current, next);
        }
    }

    // ── Manual reassign (admin) ───────────────────────────────────────────────

    @Override
    @Transactional
    public ReassignResponse reassign(Long assignmentId, String reason) {
        DeliveryAssignment existing = findById(assignmentId);

        if (TERMINAL.contains(existing.getDeliveryStatus())) {
            throw new BadRequestException(
                    "Assignment id=" + assignmentId + " is in a terminal state ("
                    + existing.getDeliveryStatus() + ") and cannot be reassigned.");
        }

        existing.setDeliveryStatus(DeliveryStatus.CANCELLED);
        existing.setFailureReason(AssignmentFailureReason.PARTNER_CANCELLED);
        existing = deliveryAssignmentRepository.save(existing);
        closingHelper.closeAttempt(existing, HistoryStatus.CANCELLED);

        Order order = existing.getOrder();
        order.setOrderStatus(com.routeassign.domain.enums.OrderStatus.PENDING);

        log.info("Manual reassign orderId={} assignmentId={} reason='{}'",
                order.getOrderId(), assignmentId, reason);

        long totalAttempts = deliveryAssignmentRepository.countByOrderId(order.getOrderId());
        AssignmentContext ctx = new AssignmentContext(
                order,
                Set.of(existing.getDeliveryPartner().getId()),
                (int) totalAttempts + 1,
                AssignmentFailureReason.PARTNER_CANCELLED);

        DeliveryAssignmentResponse newAssignment = assign(order, ctx);
        order.setOrderStatus(com.routeassign.domain.enums.OrderStatus.ASSIGNED);

        return ReassignResponse.builder()
                .oldAssignmentId(assignmentId)
                .newAssignment(newAssignment)
                .reason(reason != null ? reason : "Manual reassignment")
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DeliveryAssignment findById(Long id) {
        return deliveryAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DeliveryAssignment", "id", id));
    }

    private double remainingCapacity(UserDetails partner) {
        double cap      = partner.getCapacity()              != null ? partner.getCapacity()              : 0.0;
        double assigned = partner.getCurrentAssignedWeight() != null ? partner.getCurrentAssignedWeight() : 0.0;
        return Math.max(0.0, cap - assigned);
    }
}
