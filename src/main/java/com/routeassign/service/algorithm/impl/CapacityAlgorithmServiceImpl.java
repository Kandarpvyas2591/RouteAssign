package com.routeassign.service.algorithm.impl;

import com.routeassign.domain.entity.UserDetails;
import com.routeassign.service.algorithm.CapacityAlgorithmService;
import org.springframework.stereotype.Service;

/**
 * Algorithm 2 — Capacity eligibility check.
 *
 * Remaining Capacity = Maximum Capacity − Currently Assigned Weight
 * Eligible           = Remaining Capacity >= New Order Weight
 */
@Service
public class CapacityAlgorithmServiceImpl implements CapacityAlgorithmService {

    @Override
    public double getRemainingCapacity(UserDetails partner) {
        double maxCapacity     = partner.getCapacity()             != null ? partner.getCapacity()             : 0.0;
        double assignedWeight  = partner.getCurrentAssignedWeight() != null ? partner.getCurrentAssignedWeight() : 0.0;
        return maxCapacity - assignedWeight;
    }

    @Override
    public boolean isCapacitySufficient(UserDetails partner, double newOrderWeight) {
        return getRemainingCapacity(partner) >= newOrderWeight;
    }
}
