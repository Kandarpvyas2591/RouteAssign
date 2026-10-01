package com.routeassign.service.impl;

import com.routeassign.domain.entity.*;
import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.domain.enums.HistoryStatus;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.dto.response.ReassignResponse;
import com.routeassign.exception.BadRequestException;
import com.routeassign.exception.InvalidStatusTransitionException;
import com.routeassign.exception.NoEligiblePartnerException;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.*;
import com.routeassign.service.DeliveryAssignmentService;
import com.routeassign.service.algorithm.DistanceAlgorithmService;
import com.routeassign.service.algorithm.PartnerSelectionAlgorithmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAssignmentServiceImpl implements DeliveryAssignmentService {

    private final DeliveryAssignmentRepository     deliveryAssignmentRepository;
    private final UserDetailsRepository            userDetailsRepository;
    private final HistoryDeliveryPartnerRepository historyDeliveryPartnerRepository;
    private final HistoryVendorRepository          historyVendorRepository;
    private final PartnerSelectionAlgorithmService partnerSelectionAlgorithmService;
    private final DistanceAlgorithmService         distanceAlgorithmService;

    /** Statuses that are considered terminal — the delivery is over. */
    private static final Set<DeliveryStatus> TERMINAL =
            Set.of(DeliveryStatus.DELIVERED, DeliveryStatus.CANCELLED, DeliveryStatus.FAILED);

    // ── assign ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DeliveryAssignmentResponse assign(Order order) {

        // Step 1: Run the full selection algorithm — returns partner + pre-computed ETA
        //         + isBusy flag + workStartTime in a single result object.
        PartnerSelectionAlgorithmService.PartnerSelectionResult result =
                partnerSelectionAlgorithmService.selectPartnerWithEta(order)
                        .orElseThrow(NoEligiblePartnerException::new);

        UserDetails   partner      = result.partner();
        LocalDateTime eta          = result.eta();
        boolean       isBusy       = result.isBusy();
        LocalDateTime workStart    = result.workStartTime();
        VendorDetails vendor       = order.getVendor();

        // Step 2: Calculate distances (needed for audit columns and the response DTO)
        double distPartnerToVendor = distanceAlgorithmService.calculateDistance(
                partner.getLatitude(),  partner.getLongitude(),
                vendor.getLatitude(),   vendor.getLongitude());

        double distVendorToCustomer = distanceAlgorithmService.calculateDistance(
                vendor.getLatitude(),   vendor.getLongitude(),
                order.getDeliveryLocationLatitude(),
                order.getDeliveryLocationLongitude());

        // Step 3: Persist the assignment — ETA comes directly from the algorithm result,
        //         so no second ETA calculation is needed here.
        DeliveryAssignment assignment = DeliveryAssignment.builder()
                .order(order)
                .deliveryPartner(partner)
                .vendor(vendor)
                .expectedDeliveryTime(eta)
                .deliveryStatus(DeliveryStatus.ASSIGNED)
                .distancePartnerToVendor(distPartnerToVendor)
                .distanceVendorToCustomer(distVendorToCustomer)
                .build();

        assignment = deliveryAssignmentRepository.save(assignment);

        // Step 4: Update partner's running assigned weight.
        //         For busy partners isAvailable stays false (they are still mid-delivery);
        //         the closeAssignment() path will set it back to true when they finish.
        double newWeight = (partner.getCurrentAssignedWeight() != null
                ? partner.getCurrentAssignedWeight() : 0.0)
                + order.getTotalWeight();
        partner.setCurrentAssignedWeight(newWeight);
        userDetailsRepository.save(partner);

        log.info("Assigned orderId={} to partnerId={} isBusy={} workStart={} ETA={} totalDist={:.2f}km",
                order.getOrderId(), partner.getId(), isBusy, workStart, eta,
                distPartnerToVendor + distVendorToCustomer);

        return toResponse(assignment, isBusy, workStart);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public DeliveryAssignmentResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public DeliveryAssignmentResponse getByOrderId(Long orderId) {
        DeliveryAssignment da = deliveryAssignmentRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryAssignment", "orderId", orderId));
        return toResponse(da);
    }

    @Override
    public List<DeliveryAssignmentResponse> getByPartnerId(Long partnerId) {
        return deliveryAssignmentRepository.findAllByDeliveryPartner_Id(partnerId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<DeliveryAssignmentResponse> getByVendorId(Long vendorId) {
        return deliveryAssignmentRepository.findAllByVendor_Id(vendorId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<DeliveryAssignmentResponse> getByStatus(DeliveryStatus status) {
        if (status == null) {
            return deliveryAssignmentRepository.findAll()
                    .stream().map(this::toResponse).toList();
        }
        return deliveryAssignmentRepository.findAllByDeliveryStatus(status)
                .stream().map(this::toResponse).toList();
    }

    // ── Status lifecycle ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public DeliveryAssignmentResponse updateStatus(Long id, DeliveryStatus newStatus) {
        DeliveryAssignment da = findById(id);
        validateStatusTransition(da.getDeliveryStatus(), newStatus);

        da.setDeliveryStatus(newStatus);
        da = deliveryAssignmentRepository.save(da);

        // If reaching a terminal status, close out the assignment
        if (TERMINAL.contains(newStatus)) {
            closeAssignment(da, newStatus);
        }

        log.info("Assignment id={} status → {}", id, newStatus);
        return toResponse(da);
    }

    // ── Terminal status handling ───────────────────────────────────────────────

    /**
     * Called when an assignment reaches DELIVERED, CANCELLED, or FAILED.
     *
     * Actions:
     *  1. Create HistoryDeliveryPartner record (with completedAt = now)
     *  2. Create HistoryVendor record
     *  3. Reduce partner's currentAssignedWeight by order weight
     *  4. If partner has no more active assignments, mark them available again
     */
    private void closeAssignment(DeliveryAssignment da, DeliveryStatus terminalStatus) {
        HistoryStatus historyStatus = switch (terminalStatus) {
            case DELIVERED  -> HistoryStatus.COMPLETED;
            case CANCELLED  -> HistoryStatus.CANCELLED;
            case FAILED     -> HistoryStatus.FAILED;
            default         -> HistoryStatus.COMPLETED;
        };

        // 1. HistoryDeliveryPartner
        HistoryDeliveryPartner history = HistoryDeliveryPartner.builder()
                .order(da.getOrder())
                .deliveryPartner(da.getDeliveryPartner())
                .status(historyStatus)
                .completedAt(LocalDateTime.now())
                .isActive(true)
                .build();
        historyDeliveryPartnerRepository.save(history);

        // 2. HistoryVendor
        HistoryVendor vendorHistory = HistoryVendor.builder()
                .order(da.getOrder())
                .vendor(da.getVendor())
                .status(historyStatus)
                .isActive(true)
                .build();
        historyVendorRepository.save(vendorHistory);

        // 3. Reduce partner's assigned weight
        UserDetails partner = da.getDeliveryPartner();
        double reduced = Math.max(0.0,
                (partner.getCurrentAssignedWeight() != null ? partner.getCurrentAssignedWeight() : 0.0)
                        - da.getOrder().getTotalWeight());
        partner.setCurrentAssignedWeight(reduced);

        // 4. Re-check availability: mark available if no more active assignments remain
        List<DeliveryAssignment> stillActive =
                deliveryAssignmentRepository.findActiveAssignmentsByPartner(partner.getId());
        if (stillActive.isEmpty()) {
            partner.setIsAvailable(true);
        }
        userDetailsRepository.save(partner);

        log.info("Closed assignment id={} status={} — partnerId={} weight reduced to {}kg",
                da.getId(), terminalStatus, partner.getId(), reduced);
    }

    // ── Status transition guard ───────────────────────────────────────────────

    /**
     * Enforces the allowed status progression:
     * ASSIGNED → EN_ROUTE_TO_VENDOR → COLLECTED → EN_ROUTE_TO_CUSTOMER → DELIVERED
     *                                                                  ↘ CANCELLED / FAILED (from any non-terminal)
     */
    private void validateStatusTransition(DeliveryStatus current, DeliveryStatus next) {
        if (TERMINAL.contains(current)) {
            throw new InvalidStatusTransitionException("DeliveryAssignment", current, next);
        }
        // CANCELLED and FAILED are always allowed from any non-terminal state
        if (next == DeliveryStatus.CANCELLED || next == DeliveryStatus.FAILED) return;

        boolean valid = switch (current) {
            case ASSIGNED              -> next == DeliveryStatus.EN_ROUTE_TO_VENDOR;
            case EN_ROUTE_TO_VENDOR    -> next == DeliveryStatus.COLLECTED;
            case COLLECTED             -> next == DeliveryStatus.EN_ROUTE_TO_CUSTOMER;
            case EN_ROUTE_TO_CUSTOMER  -> next == DeliveryStatus.DELIVERED;
            default                    -> false;
        };

        if (!valid) {
            throw new InvalidStatusTransitionException("DeliveryAssignment", current, next);
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private DeliveryAssignment findById(Long id) {
        return deliveryAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryAssignment", "id", id));
    }

    /**
     * Maps a persisted {@link DeliveryAssignment} to the response DTO.
     * The {@code isBusy} and {@code workStartTime} fields are only known at
     * assignment creation time (they come from the algorithm result), so they
     * must be passed in explicitly when building the create-response.
     * For read-only queries they default to {@code false} / {@code null}
     * because the busy context is already reflected in {@code expectedDeliveryTime}.
     */
    private DeliveryAssignmentResponse toResponse(DeliveryAssignment da,
                                                   Boolean isBusy,
                                                   LocalDateTime workStartTime) {
        double partnerToVendor  = da.getDistancePartnerToVendor()  != null ? da.getDistancePartnerToVendor()  : 0.0;
        double vendorToCustomer = da.getDistanceVendorToCustomer() != null ? da.getDistanceVendorToCustomer() : 0.0;

        return DeliveryAssignmentResponse.builder()
                .id(da.getId())
                .orderId(da.getOrder().getOrderId())
                .deliveryPartnerId(da.getDeliveryPartner().getId())
                .deliveryPartnerName(da.getDeliveryPartner().getAuth().getUsername())
                .vendorId(da.getVendor().getId())
                .vendorName(da.getVendor().getAuth().getUsername())
                .assignedAt(da.getAssignedAt())
                .expectedDeliveryTime(da.getExpectedDeliveryTime())
                .deliveryStatus(da.getDeliveryStatus())
                .distancePartnerToVendor(partnerToVendor)
                .distanceVendorToCustomer(vendorToCustomer)
                .totalDistance(partnerToVendor + vendorToCustomer)
                .isPartnerBusy(isBusy != null ? isBusy : false)
                .workStartTime(workStartTime)
                .build();
    }

    /** Convenience overload for read-only queries (no busy context available). */
    private DeliveryAssignmentResponse toResponse(DeliveryAssignment da) {
        return toResponse(da, false, null);
    }

    // ── Reassign ──────────────────────────────────────────────────────────────

    /**
     * Cancels the current assignment and immediately re-runs the algorithm
     * on the same order to find a replacement partner.
     *
     * Flow:
     *  1. Validate that the assignment is not already in a terminal state.
     *  2. Cancel the current assignment (triggers history creation + weight restore).
     *  3. Reset the order status back to PENDING so the order can be re-assigned.
     *  4. Run assign() on the same order — throws NoEligiblePartnerException if no one available.
     *  5. Return both the old assignment ID and the new assignment in ReassignResponse.
     */
    @Override
    @Transactional
    public ReassignResponse reassign(Long assignmentId, String reason) {
        DeliveryAssignment existing = findById(assignmentId);

        if (TERMINAL.contains(existing.getDeliveryStatus())) {
            throw new BadRequestException(
                    "Assignment id=" + assignmentId + " is already in a terminal state ("
                    + existing.getDeliveryStatus() + ") and cannot be reassigned.");
        }

        // Cancel the existing assignment — this restores partner weight and creates history
        existing.setDeliveryStatus(DeliveryStatus.CANCELLED);
        existing = deliveryAssignmentRepository.save(existing);
        closeAssignment(existing, DeliveryStatus.CANCELLED);

        // Reset order back to PENDING so the algorithm treats it as a fresh order
        Order order = existing.getOrder();
        order.setOrderStatus(com.routeassign.domain.enums.OrderStatus.PENDING);

        log.info("Reassigning orderId={} assignmentId={} reason='{}'",
                order.getOrderId(), assignmentId, reason);

        // Run the full algorithm again
        DeliveryAssignmentResponse newAssignment = assign(order);

        // Mark the order ASSIGNED again
        order.setOrderStatus(com.routeassign.domain.enums.OrderStatus.ASSIGNED);

        return ReassignResponse.builder()
                .oldAssignmentId(assignmentId)
                .newAssignment(newAssignment)
                .reason(reason != null ? reason : "Manual reassignment")
                .build();
    }
}
