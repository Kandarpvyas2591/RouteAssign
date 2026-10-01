package com.routeassign.service.algorithm.impl;

import com.routeassign.config.BusinessRulesConfig;
import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.UserDetails;
import com.routeassign.repository.DeliveryAssignmentRepository;
import com.routeassign.repository.HistoryDeliveryPartnerRepository;
import com.routeassign.repository.UserDetailsRepository;
import com.routeassign.service.algorithm.DeliveryTimeAlgorithmService;
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
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  FULL SELECTION FLOW                                                    │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │  Pool A — Free partners                                                 │
 * │    1. Active + available + remaining capacity ≥ order weight            │
 * │    2. Same-vendor reuse check (subset of Pool A)                        │
 * │    3. ETA = normal working-hour calculation from now                    │
 * │                                                                         │
 * │  Pool B — Busy partners (cross-vendor reuse)                            │
 * │    4. Active + isAvailable=false + remaining capacity ≥ order weight    │
 * │    5. Must NOT already be in Pool A (no double-counting)                │
 * │    6. ETA = currentDeliveryEta + rest + travel, snapped to window       │
 * │                                                                         │
 * │  Merge A + B → compare by earliest ETA                                 │
 * │    7. Keep all candidates whose ETA equals the minimum ETA              │
 * │    8. Tie-breaking (applied to equal-ETA candidates):                   │
 * │       a. Never previously assigned  → prefer new partners               │
 * │       b. Multiple new partners      → pick one randomly                 │
 * │       c. No new partners            → highest rating                    │
 * │       d. Same rating                → longest idle time                 │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerSelectionAlgorithmServiceImpl implements PartnerSelectionAlgorithmService {

    private final UserDetailsRepository            userDetailsRepository;
    private final DeliveryAssignmentRepository     deliveryAssignmentRepository;
    private final HistoryDeliveryPartnerRepository historyDeliveryPartnerRepository;
    private final DistanceAlgorithmService         distanceAlgorithmService;
    private final DeliveryTimeAlgorithmService     deliveryTimeAlgorithmService;
    private final BusinessRulesConfig              businessRules;

    // ── Internal candidate record (richer than the public result) ─────────────
    // Carries everything needed for scoring + tie-breaking inside this class.
    private record Candidate(
            UserDetails   partner,
            LocalDateTime eta,
            boolean       isBusy,
            LocalDateTime workStartTime,
            double        totalDistance
    ) {}

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Optional<PartnerSelectionResult> selectPartnerWithEta(Order order) {
        log.info("Starting partner selection for orderId={}", order.getOrderId());

        LocalDateTime now = LocalDateTime.now();

        // ── Pool A: free eligible partners ───────────────────────────────────
        List<UserDetails> freePartners = userDetailsRepository
                .findEligibleDeliveryPartners(order.getTotalWeight());

        // Apply same-vendor preference within the free pool
        List<UserDetails> sameVendorFree = filterSameVendorEligible(freePartners, order);
        List<UserDetails> freePool = sameVendorFree.isEmpty() ? freePartners : sameVendorFree;

        // Build Candidate objects for free partners
        List<Candidate> candidates = buildFreeCandidates(freePool, order, now);

        // ── Pool B: busy partners (cross-vendor reuse) ────────────────────────
        if (businessRules.isEnableBusyPartnerReuse()) {
            Set<Long> freeIds = freePartners.stream()
                    .map(UserDetails::getId)
                    .collect(Collectors.toSet());

            List<UserDetails> busyPartners = userDetailsRepository
                    .findBusyEligibleDeliveryPartners(order.getTotalWeight())
                    .stream()
                    .filter(p -> !freeIds.contains(p.getId()))  // exclude already-free partners
                    .toList();

            List<Candidate> busyCandidates = buildBusyCandidates(busyPartners, order);
            candidates = new ArrayList<>(candidates);
            candidates.addAll(busyCandidates);

            log.debug("Candidate pool: {} free + {} busy for orderId={}",
                    freePool.size(), busyCandidates.size(), order.getOrderId());
        }

        if (candidates.isEmpty()) {
            log.warn("No eligible partners (free or busy) for orderId={}", order.getOrderId());
            return Optional.empty();
        }

        // ── Find earliest ETA ─────────────────────────────────────────────────
        LocalDateTime earliestEta = candidates.stream()
                .map(Candidate::eta)
                .min(LocalDateTime::compareTo)
                .orElseThrow();

        List<Candidate> shortlisted = candidates.stream()
                .filter(c -> !c.eta().isAfter(earliestEta.plusMinutes(1))) // 1-min tolerance
                .collect(Collectors.toList());

        log.debug("Shortlisted {} candidate(s) with earliest ETA {} for orderId={}",
                shortlisted.size(), earliestEta, order.getOrderId());

        // ── Tie-breaking ──────────────────────────────────────────────────────
        Candidate winner = applyTieBreaking(shortlisted);

        log.info("Selected partnerId={} isBusy={} ETA={} for orderId={}",
                winner.partner().getId(), winner.isBusy(), winner.eta(), order.getOrderId());

        return Optional.of(new PartnerSelectionResult(
                winner.partner(),
                winner.eta(),
                winner.isBusy(),
                winner.workStartTime()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pool A helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the subset of free partners already assigned to the same vendor
     * whose extra distance to the new delivery location is within the threshold.
     */
    private List<UserDetails> filterSameVendorEligible(List<UserDetails> eligible, Order order) {
        Long vendorId           = order.getVendor().getId();
        double maxExtraDistance = businessRules.getMaxAdditionalDeliveryDistance();
        double vendorLat        = order.getVendor().getLatitude();
        double vendorLon        = order.getVendor().getLongitude();
        double newCustomerLat   = order.getDeliveryLocationLatitude();
        double newCustomerLon   = order.getDeliveryLocationLongitude();

        List<UserDetails> result = new ArrayList<>();
        for (UserDetails partner : eligible) {
            boolean activeAtVendor = !deliveryAssignmentRepository
                    .findActiveAssignmentsByPartnerAndVendor(partner.getId(), vendorId)
                    .isEmpty();

            if (!activeAtVendor) continue;

            double extraDist = distanceAlgorithmService.calculateDistance(
                    vendorLat, vendorLon, newCustomerLat, newCustomerLon);

            if (extraDist <= maxExtraDistance) {
                result.add(partner);
                log.debug("Same-vendor reuse eligible: partnerId={} extraDist={:.2f}km",
                        partner.getId(), extraDist);
            }
        }
        return result;
    }

    /**
     * Computes Candidate objects for the free partner pool.
     * ETA uses the standard working-hour calculation from {@code now}.
     */
    private List<Candidate> buildFreeCandidates(List<UserDetails> freePool,
                                                Order order,
                                                LocalDateTime now) {
        double vendorLat   = order.getVendor().getLatitude();
        double vendorLon   = order.getVendor().getLongitude();
        double customerLat = order.getDeliveryLocationLatitude();
        double customerLon = order.getDeliveryLocationLongitude();

        List<Candidate> list = new ArrayList<>();
        for (UserDetails partner : freePool) {
            double distToVendor = distanceAlgorithmService.calculateDistance(
                    partner.getLatitude(), partner.getLongitude(), vendorLat, vendorLon);
            double distToCustomer = distanceAlgorithmService.calculateDistance(
                    vendorLat, vendorLon, customerLat, customerLon);
            double totalDist = distToVendor + distToCustomer;

            LocalDateTime workStart = deliveryTimeAlgorithmService
                    .determineWorkStartTime(now, distToVendor);
            LocalDateTime eta = deliveryTimeAlgorithmService
                    .calculateExpectedDeliveryTime(now, distToVendor, distToCustomer);

            list.add(new Candidate(partner, eta, false, workStart, totalDist));
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pool B helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Computes Candidate objects for the busy partner pool.
     *
     * ETA formula:
     *   restReadyTime = latestActiveAssignment.expectedDeliveryTime + restDurationMinutes
     *   workStart     = snap(restReadyTime, distancePartnerToVendor)
     *   eta           = scheduleWithinWorkingHours(workStart, totalTravelHours)
     *
     * Partners whose active assignment has no expectedDeliveryTime stored are skipped.
     */
    private List<Candidate> buildBusyCandidates(List<UserDetails> busyPartners, Order order) {
        double vendorLat   = order.getVendor().getLatitude();
        double vendorLon   = order.getVendor().getLongitude();
        double customerLat = order.getDeliveryLocationLatitude();
        double customerLon = order.getDeliveryLocationLongitude();
        int restMinutes    = businessRules.getPartnerRestDurationMinutes();

        List<Candidate> list = new ArrayList<>();
        for (UserDetails partner : busyPartners) {

            // Find the latest active assignment to get current ETA
            Optional<DeliveryAssignment> latestOpt =
                    deliveryAssignmentRepository.findLatestActiveAssignmentByPartner(partner.getId());

            if (latestOpt.isEmpty()) continue;

            LocalDateTime currentEta = latestOpt.get().getExpectedDeliveryTime();
            if (currentEta == null) {
                // Cannot compute rest-adjusted ETA without a known current ETA — skip
                log.debug("Skipping busy partnerId={}: no expectedDeliveryTime on active assignment",
                        partner.getId());
                continue;
            }

            double distToVendor = distanceAlgorithmService.calculateDistance(
                    partner.getLatitude(), partner.getLongitude(), vendorLat, vendorLon);
            double distToCustomer = distanceAlgorithmService.calculateDistance(
                    vendorLat, vendorLon, customerLat, customerLon);
            double totalDist = distToVendor + distToCustomer;

            // Work start = restReadyTime snapped to valid window
            LocalDateTime restReadyTime = currentEta.plusMinutes(restMinutes);
            LocalDateTime workStart = deliveryTimeAlgorithmService
                    .determineWorkStartTime(restReadyTime, distToVendor);

            LocalDateTime eta = deliveryTimeAlgorithmService.calculateEtaForBusyPartner(
                    currentEta, restMinutes, distToVendor, distToCustomer);

            list.add(new Candidate(partner, eta, true, workStart, totalDist));

            log.debug("Busy candidate partnerId={} currentEta={} restReadyTime={} eta={}",
                    partner.getId(), currentEta, restReadyTime, eta);
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tie-breaking (operates on Candidate, not raw UserDetails)
    // ─────────────────────────────────────────────────────────────────────────

    private Candidate applyTieBreaking(List<Candidate> shortlisted) {
        if (shortlisted.size() == 1) return shortlisted.get(0);

        // Prefer free partners over busy partners first
        List<Candidate> freeOnly = shortlisted.stream()
                .filter(c -> !c.isBusy())
                .collect(Collectors.toList());
        List<Candidate> pool = freeOnly.isEmpty() ? shortlisted : freeOnly;

        if (pool.size() == 1) {
            log.debug("Tie-break: only one {} partner", freeOnly.isEmpty() ? "busy" : "free");
            return pool.get(0);
        }

        // 5a: never previously assigned
        List<Candidate> neverAssigned = pool.stream()
                .filter(c -> !historyDeliveryPartnerRepository
                        .existsByDeliveryPartner_Id(c.partner().getId()))
                .collect(Collectors.toList());

        if (!neverAssigned.isEmpty()) {
            if (neverAssigned.size() == 1) return neverAssigned.get(0);
            // 5b: multiple never-assigned → random
            log.debug("Tie-break 5b: random among {} never-assigned", neverAssigned.size());
            return neverAssigned.get(new Random().nextInt(neverAssigned.size()));
        }

        // 5c: highest rating
        double maxRating = pool.stream()
                .mapToDouble(c -> c.partner().getRating() != null ? c.partner().getRating() : 0.0)
                .max().orElse(0.0);

        List<Candidate> topRated = pool.stream()
                .filter(c -> {
                    double r = c.partner().getRating() != null ? c.partner().getRating() : 0.0;
                    return Math.abs(r - maxRating) < 1e-9;
                })
                .collect(Collectors.toList());

        if (topRated.size() == 1) return topRated.get(0);

        // 5d: longest idle time
        log.debug("Tie-break 5d: idle time among {} equal-rated candidates", topRated.size());
        return topRated.stream()
                .max(Comparator.comparingLong(c -> getIdleTimeMillis(c.partner())))
                .orElse(topRated.get(0));
    }

    private long getIdleTimeMillis(UserDetails partner) {
        return historyDeliveryPartnerRepository
                .findLatestCompletedByPartner(partner.getId())
                .map(h -> Duration.between(h.getCompletedAt(), LocalDateTime.now()).toMillis())
                .orElse(Long.MAX_VALUE);
    }
}
