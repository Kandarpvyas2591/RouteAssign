package com.routeassign.service.algorithm.impl;

import com.routeassign.config.BusinessRulesConfig;
import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.UserDetails;
import com.routeassign.repository.DeliveryAssignmentRepository;
import com.routeassign.repository.HistoryDeliveryPartnerRepository;
import com.routeassign.repository.UserDetailsRepository;
import com.routeassign.service.algorithm.DistanceAlgorithmService;
import com.routeassign.service.algorithm.PartnerSelectionAlgorithmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Algorithm 4 — Main Delivery Partner Selection.
 *
 * Full selection flow:
 *
 *  Step 1  Eligibility: active + available + remaining capacity ≥ order weight
 *          (delegated to UserDetailsRepository.findEligibleDeliveryPartners)
 *
 *  Step 2  Same-vendor reuse:
 *          For each eligible partner already assigned to the same vendor,
 *          check whether the extra distance to the new customer location
 *          is ≤ MAX_ADDITIONAL_DELIVERY_DISTANCE (configurable).
 *          Partners that pass become the preferred candidate pool;
 *          if none pass, fall back to the full eligible list.
 *
 *  Step 3  Score every candidate by total Haversine distance:
 *          Partner Home → Vendor  +  Vendor → Customer
 *
 *  Step 4  Keep only candidates whose score equals the minimum.
 *
 *  Step 5  Tie-breaking (applied in order):
 *          a. "Never assigned" partners (no history record) — prefer these
 *          b. Multiple never-assigned → pick one randomly
 *          c. No never-assigned → highest rating
 *          d. Same rating → longest idle time (current − lastCompletedAt)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerSelectionAlgorithmServiceImpl implements PartnerSelectionAlgorithmService {

    private final UserDetailsRepository              userDetailsRepository;
    private final DeliveryAssignmentRepository       deliveryAssignmentRepository;
    private final HistoryDeliveryPartnerRepository   historyDeliveryPartnerRepository;
    private final DistanceAlgorithmService           distanceAlgorithmService;
    private final BusinessRulesConfig                businessRules;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Optional<UserDetails> selectPartner(Order order) {
        log.info("Starting partner selection for orderId={}", order.getOrderId());

        // ── Step 1: Eligibility filter ────────────────────────────────────────
        List<UserDetails> eligible = userDetailsRepository
                .findEligibleDeliveryPartners(order.getTotalWeight());

        if (eligible.isEmpty()) {
            log.warn("No eligible delivery partners for orderId={}", order.getOrderId());
            return Optional.empty();
        }

        // ── Step 2: Same-vendor reuse ─────────────────────────────────────────
        List<UserDetails> sameVendorCandidates = filterSameVendorEligible(eligible, order);

        // Use same-vendor pool if any passed; otherwise use the full eligible pool
        List<UserDetails> candidates = sameVendorCandidates.isEmpty() ? eligible : sameVendorCandidates;

        // ── Step 3: Score by total Haversine distance ─────────────────────────
        Map<UserDetails, Double> scored = scoreByDistance(candidates, order);

        // ── Step 4: Keep candidates at minimum distance ───────────────────────
        double minDist = Collections.min(scored.values());
        List<UserDetails> shortlisted = scored.entrySet().stream()
                .filter(e -> e.getValue() <= minDist + 1e-9)   // floating-point tolerance
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.debug("Shortlisted {} partner(s) at distance ~{} km for orderId={}",
                shortlisted.size(), String.format("%.2f", minDist), order.getOrderId());

        // ── Step 5: Tie-breaking ──────────────────────────────────────────────
        Optional<UserDetails> selected = applyTieBreaking(shortlisted);
        selected.ifPresent(p -> log.info("Selected partnerId={} for orderId={}",
                p.getId(), order.getOrderId()));
        return selected;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2 helper: same-vendor reuse
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the subset of eligible partners who are already assigned to the
     * same vendor AND whose additional travel distance to the new delivery
     * location is within the configured threshold.
     */
    private List<UserDetails> filterSameVendorEligible(List<UserDetails> eligible, Order order) {
        Long vendorId           = order.getVendor().getId();
        double maxExtraDistance = businessRules.getMaxAdditionalDeliveryDistance();

        double newCustomerLat = order.getDeliveryLocationLatitude();
        double newCustomerLon = order.getDeliveryLocationLongitude();
        double vendorLat      = order.getVendor().getLatitude();
        double vendorLon      = order.getVendor().getLongitude();

        List<UserDetails> sameVendorPartners = new ArrayList<>();

        for (UserDetails partner : eligible) {
            List<DeliveryAssignment> activeAtVendor =
                    deliveryAssignmentRepository.findActiveAssignmentsByPartnerAndVendor(
                            partner.getId(), vendorId);

            if (activeAtVendor.isEmpty()) continue;

            // Check the extra distance from vendor to new customer
            double extraDistance = distanceAlgorithmService.calculateDistance(
                    vendorLat, vendorLon, newCustomerLat, newCustomerLon);

            if (extraDistance <= maxExtraDistance) {
                sameVendorPartners.add(partner);
                log.debug("Partner {} reuse eligible: extra distance {:.2f} km ≤ threshold {:.2f} km",
                        partner.getId(), extraDistance, maxExtraDistance);
            }
        }

        return sameVendorPartners;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 3 helper: distance scoring
    // ─────────────────────────────────────────────────────────────────────────

    private Map<UserDetails, Double> scoreByDistance(List<UserDetails> candidates, Order order) {
        double vendorLat     = order.getVendor().getLatitude();
        double vendorLon     = order.getVendor().getLongitude();
        double customerLat   = order.getDeliveryLocationLatitude();
        double customerLon   = order.getDeliveryLocationLongitude();

        Map<UserDetails, Double> scores = new LinkedHashMap<>();
        for (UserDetails partner : candidates) {
            double total = distanceAlgorithmService.calculateTotalAssignmentDistance(
                    partner.getLatitude(), partner.getLongitude(),
                    vendorLat, vendorLon,
                    customerLat, customerLon);
            scores.put(partner, total);
        }
        return scores;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 5: tie-breaking
    // ─────────────────────────────────────────────────────────────────────────

    private Optional<UserDetails> applyTieBreaking(List<UserDetails> shortlisted) {
        if (shortlisted.isEmpty()) return Optional.empty();
        if (shortlisted.size() == 1) return Optional.of(shortlisted.get(0));

        // 5a: prefer never-assigned partners
        List<UserDetails> neverAssigned = shortlisted.stream()
                .filter(p -> !historyDeliveryPartnerRepository.existsByDeliveryPartner_Id(p.getId()))
                .collect(Collectors.toList());

        if (!neverAssigned.isEmpty()) {
            if (neverAssigned.size() == 1) return Optional.of(neverAssigned.get(0));

            // 5b: multiple never-assigned → random
            log.debug("Tie-break 5b: random among {} never-assigned partners", neverAssigned.size());
            return Optional.of(neverAssigned.get(new Random().nextInt(neverAssigned.size())));
        }

        // 5c: no never-assigned → highest rating
        double maxRating = shortlisted.stream()
                .mapToDouble(p -> p.getRating() != null ? p.getRating() : 0.0)
                .max()
                .orElse(0.0);

        List<UserDetails> topRated = shortlisted.stream()
                .filter(p -> {
                    double r = p.getRating() != null ? p.getRating() : 0.0;
                    return Math.abs(r - maxRating) < 1e-9;
                })
                .collect(Collectors.toList());

        if (topRated.size() == 1) return Optional.of(topRated.get(0));

        // 5d: same rating → longest idle time
        log.debug("Tie-break 5d: idle time among {} partners with rating {}", topRated.size(), maxRating);
        return topRated.stream()
                .max(Comparator.comparingLong(this::getIdleTimeMillis));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Idle-time helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns milliseconds since the partner's last completed delivery.
     * Partners who have never completed a delivery get Long.MAX_VALUE so they
     * are ranked as "most idle" (they should have been caught by the
     * never-assigned check above, but this provides a safe fallback).
     */
    private long getIdleTimeMillis(UserDetails partner) {
        return historyDeliveryPartnerRepository
                .findLatestCompletedByPartner(partner.getId())
                .map(h -> Duration.between(h.getCompletedAt(), LocalDateTime.now()).toMillis())
                .orElse(Long.MAX_VALUE);
    }
}
