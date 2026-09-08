package com.routeassign.service.impl;

import com.routeassign.domain.entity.*;
import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.domain.enums.HistoryStatus;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.exception.InvalidStatusTransitionException;
import com.routeassign.exception.NoEligiblePartnerException;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.*;
import com.routeassign.service.DeliveryAssignmentService;
import com.routeassign.service.algorithm.DeliveryTimeAlgorithmService;
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
    private final DeliveryTimeAlgorithmService     deliveryTimeAlgorithmService;

    /** Statuses that are considered terminal — the delivery is over. */
    private static final Set<DeliveryStatus> TERMINAL =
            Set.of(DeliveryStatus.DELIVERED, DeliveryStatus.CANCELLED, DeliveryStatus.FAILED);

    // ── assign ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DeliveryAssignmentResponse assign(Order order) {

        // Step 1: Select the best partner (throws nothing — returns empty if none found)
        UserDetails partner = partnerSelectionAlgorithmService.selectPartner(order)
                .orElseThrow(NoEligiblePartnerException::new);

        VendorDetails vendor = order.getVendor();

        // Step 2: Calculate distances
        double distPartnerToVendor = distanceAlgorithmService.calculateDistance(
                partner.getLatitude(),  partner.getLongitude(),
                vendor.getLatitude(),   vendor.getLongitude());

        double distVendorToCustomer = distanceAlgorithmService.calculateDistance(
                vendor.getLatitude(),   vendor.getLongitude(),
                order.getDeliveryLocationLatitude(),
                order.getDeliveryLocationLongitude());

        // Step 3: Calculate ETA
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime eta = deliveryTimeAlgorithmService.calculateExpectedDeliveryTime(
                now, distPartnerToVendor, distVendorToCustomer);

        // Step 4: Persist the assignment
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

        // Step 5: Update partner's running assigned weight
        double newWeight = (partner.getCurrentAssignedWeight() != null
                ? partner.getCurrentAssignedWeight() : 0.0)
                + order.getTotalWeight();
        partner.setCurrentAssignedWeight(newWeight);
        userDetailsRepository.save(partner);

        log.info("Assigned orderId={} to partnerId={} ETA={} dist={:.2f}km",
                order.getOrderId(), partner.getId(), eta,
                distPartnerToVendor + distVendorToCustomer);

        return toResponse(assignment);
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

    private DeliveryAssignmentResponse toResponse(DeliveryAssignment da) {
        double partnerToVendor   = da.getDistancePartnerToVendor()   != null ? da.getDistancePartnerToVendor()   : 0.0;
        double vendorToCustomer  = da.getDistanceVendorToCustomer()  != null ? da.getDistanceVendorToCustomer()  : 0.0;

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
                .build();
    }
}
