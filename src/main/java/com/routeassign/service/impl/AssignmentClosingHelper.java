package com.routeassign.service.impl;

import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.domain.entity.HistoryDeliveryPartner;
import com.routeassign.domain.entity.HistoryVendor;
import com.routeassign.domain.entity.UserDetails;
import com.routeassign.domain.enums.HistoryStatus;
import com.routeassign.repository.DeliveryAssignmentRepository;
import com.routeassign.repository.HistoryDeliveryPartnerRepository;
import com.routeassign.repository.HistoryVendorRepository;
import com.routeassign.repository.UserDetailsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Single source of truth for the side effects that occur whenever an assignment
 * attempt ends — regardless of whether the ending was successful (DELIVERED),
 * a failure (REJECTED, EXPIRED, DELIVERY_FAILED), or a cancellation (CANCELLED).
 *
 * Previously this logic was duplicated between
 * {@link AssignmentLifecycleServiceImpl#closePartnerAssignment} and
 * {@link DeliveryAssignmentServiceImpl#closeAssignment}. A single change here
 * now propagates to both callers correctly.
 *
 * Responsibilities:
 * <ol>
 *   <li>Persist a {@link HistoryDeliveryPartner} audit record.</li>
 *   <li>Persist a {@link HistoryVendor} audit record.</li>
 *   <li>Reduce the partner's {@code currentAssignedWeight} by the order weight.</li>
 *   <li>Restore {@code isAvailable = true} if no other active assignments remain.</li>
 * </ol>
 *
 * This component does NOT change the assignment's {@code DeliveryStatus} — that
 * is the caller's responsibility so the caller retains full control over the
 * status machine.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentClosingHelper {

    private final HistoryDeliveryPartnerRepository historyDeliveryPartnerRepository;
    private final HistoryVendorRepository          historyVendorRepository;
    private final DeliveryAssignmentRepository     deliveryAssignmentRepository;
    private final UserDetailsRepository            userDetailsRepository;

    /**
     * Closes a delivery attempt by writing history records and restoring
     * the partner's state.
     *
     * @param da            the assignment that is ending
     * @param historyStatus the outcome to record in history
     *                      ({@link HistoryStatus#COMPLETED}, {@link HistoryStatus#CANCELLED},
     *                      or {@link HistoryStatus#FAILED})
     */
    public void closeAttempt(DeliveryAssignment da, HistoryStatus historyStatus) {
        UserDetails partner = da.getDeliveryPartner();

        // 1. History record for the delivery partner
        historyDeliveryPartnerRepository.save(HistoryDeliveryPartner.builder()
                .order(da.getOrder())
                .deliveryPartner(partner)
                .status(historyStatus)
                .completedAt(LocalDateTime.now())
                .isActive(true)
                .build());

        // 2. History record for the vendor
        historyVendorRepository.save(HistoryVendor.builder()
                .order(da.getOrder())
                .vendor(da.getVendor())
                .status(historyStatus)
                .isActive(true)
                .build());

        // 3. Reduce the partner's tracked assigned weight (floor at 0 to guard
        //    against any floating-point drift or race condition edge cases)
        double orderWeight = da.getOrder().getTotalWeight() != null
                ? da.getOrder().getTotalWeight() : 0.0;
        double current     = partner.getCurrentAssignedWeight() != null
                ? partner.getCurrentAssignedWeight() : 0.0;
        double reduced     = Math.max(0.0, current - orderWeight);
        partner.setCurrentAssignedWeight(reduced);

        // 4. Restore availability if this was the partner's last active assignment
        List<DeliveryAssignment> stillActive =
                deliveryAssignmentRepository.findActiveAssignmentsByPartner(partner.getId());
        if (stillActive.isEmpty()) {
            partner.setIsAvailable(true);
        }

        userDetailsRepository.save(partner);

        log.info("Closed attempt assignmentId={} historyStatus={} partnerId={} weightReduced→{}kg",
                da.getId(), historyStatus, partner.getId(), reduced);
    }
}
