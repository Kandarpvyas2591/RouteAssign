package com.routeassign.service.algorithm;

import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.UserDetails;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Algorithm 4 — Main Delivery Partner Selection Algorithm.
 *
 * Orchestrates all sub-algorithms and business rules to select the single
 * most appropriate delivery partner for a given order.
 *
 * Selection steps (in order):
 *  1. Filter: active, available, sufficient remaining capacity  (free partners)
 *  2. Same-vendor reuse check (within configurable distance threshold)
 *  3. Busy-partner cross-vendor reuse (if enableBusyPartnerReuse = true):
 *       - active, currently unavailable, but still has remaining capacity
 *       - ETA computed as: currentDeliveryEta + rest + travel, snapped to working window
 *  4. All candidates (free + busy) compared by earliest ETA
 *  5. Tie-breaking on equal ETA:
 *       a. Never previously assigned  (new partner priority)
 *       b. Random selection among tied new partners
 *       c. Highest rating              (if no new partners remain)
 *       d. Longest idle time           (if same rating)
 */
public interface PartnerSelectionAlgorithmService {

    /**
     * Selects the best delivery partner for the given order and returns
     * their pre-computed ETA together with a flag indicating whether they
     * are currently busy on another delivery.
     *
     * This is the primary method used by {@code DeliveryAssignmentServiceImpl}
     * so it can persist the ETA without recalculating it.
     *
     * @param order the newly placed order (must already be persisted)
     * @return the selection result, or empty if no eligible partner exists
     */
    Optional<PartnerSelectionResult> selectPartnerWithEta(Order order);

    /**
     * Convenience method — returns just the selected partner.
     * Delegates to {@link #selectPartnerWithEta(Order)}.
     *
     * @param order the newly placed order
     * @return the selected UserDetails, or empty if none eligible
     */
    default Optional<UserDetails> selectPartner(Order order) {
        return selectPartnerWithEta(order).map(PartnerSelectionResult::partner);
    }

    // ── Result record ─────────────────────────────────────────────────────────

    /**
     * Immutable result of the partner selection algorithm.
     *
     * @param partner       the selected delivery partner
     * @param eta           pre-computed expected delivery time
     * @param isBusy        true if the partner is currently mid-delivery
     *                      (cross-vendor busy-partner reuse path)
     * @param workStartTime the time the partner will begin travelling for this order
     *                      (after current delivery + rest for busy partners,
     *                       or the normal work-start for free partners)
     */
    record PartnerSelectionResult(
            UserDetails   partner,
            LocalDateTime eta,
            boolean       isBusy,
            LocalDateTime workStartTime
    ) {}
}
