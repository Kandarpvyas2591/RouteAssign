package com.routeassign.service.algorithm;

import com.routeassign.domain.entity.UserDetails;

/**
 * Algorithm 2 — Capacity Eligibility Check.
 *
 * Determines whether a delivery partner can accept a new order
 * without exceeding their maximum carrying capacity.
 */
public interface CapacityAlgorithmService {

    /**
     * Returns the remaining capacity of a delivery partner.
     *
     * Remaining Capacity = Maximum Capacity − Currently Assigned Weight
     *
     * @param partner the delivery partner
     * @return remaining capacity in kg
     */
    double getRemainingCapacity(UserDetails partner);

    /**
     * Checks whether the partner can carry the new order weight.
     *
     * @param partner        the delivery partner
     * @param newOrderWeight weight of the new order in kg
     * @return true if Remaining Capacity >= newOrderWeight
     */
    boolean isCapacitySufficient(UserDetails partner, double newOrderWeight);
}
