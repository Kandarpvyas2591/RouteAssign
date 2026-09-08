package com.routeassign.service.algorithm;

import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.UserDetails;

import java.util.Optional;

/**
 * Algorithm 4 — Main Delivery Partner Selection Algorithm.
 *
 * Orchestrates all sub-algorithms and business rules to select the single
 * most appropriate delivery partner for a given order.
 *
 * Selection steps (in order):
 *  1. Filter: active, available, sufficient remaining capacity
 *  2. Same-vendor reuse check (within configurable distance threshold)
 *  3. Haversine distance calculation (Home→Vendor + Vendor→Customer)
 *  4. Select candidate(s) with shortest valid total distance
 *  5. Tie-breaking:
 *       a. Never previously assigned  (new partner priority)
 *       b. Random selection among tied new partners
 *       c. Highest rating              (if no new partners remain)
 *       d. Longest idle time           (if same rating)
 *  6. Working-hour and after-5-PM rule validation
 *  7. ETA calculation via DeliveryTimeAlgorithmService
 */
public interface PartnerSelectionAlgorithmService {

    /**
     * Selects the best delivery partner for the given order.
     *
     * @param order the newly placed order (must already be persisted)
     * @return the selected UserDetails (delivery partner), or empty if none eligible
     */
    Optional<UserDetails> selectPartner(Order order);
}
