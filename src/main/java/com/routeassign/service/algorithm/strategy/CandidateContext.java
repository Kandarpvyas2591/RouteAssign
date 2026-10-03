package com.routeassign.service.algorithm.strategy;

import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.UserDetails;

/**
 * Immutable snapshot of all pre-computed inputs that any scoring strategy
 * may need to evaluate a candidate delivery partner for a specific order.
 *
 * The context is assembled once by {@code PartnerSelectionAlgorithmServiceImpl}
 * before invoking the scoring engine, so each strategy receives clean inputs
 * rather than having to re-query repositories on every call.
 *
 * <pre>
 * PartnerSelectionAlgorithmServiceImpl
 *        ↓  builds one CandidateContext per candidate
 * AssignmentScoringEngineImpl
 *        ↓  passes CandidateContext to each strategy
 * AssignmentScoringStrategy.calculateScore(ctx, config)
 * </pre>
 *
 * @param partner           the candidate delivery partner
 * @param order             the order being assigned
 * @param distanceToVendorKm   pre-computed partner→vendor distance (km)
 * @param distanceVendorToCustomerKm  pre-computed vendor→customer distance (km)
 * @param totalDistanceKm   distanceToVendorKm + distanceVendorToCustomerKm
 * @param remainingCapacityKg  partner capacity − currently assigned weight (kg)
 * @param idleMinutes       minutes since partner's last completed delivery;
 *                          {@link Long#MAX_VALUE} if the partner has never been assigned
 * @param isSameVendor      true if the partner already has an active assignment
 *                          at the same vendor as this order
 */
public record CandidateContext(
        UserDetails partner,
        Order       order,
        double      distanceToVendorKm,
        double      distanceVendorToCustomerKm,
        double      totalDistanceKm,
        double      remainingCapacityKg,
        long        idleMinutes,
        boolean     isSameVendor
) {}
