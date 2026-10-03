package com.routeassign.service.algorithm.impl;

import com.routeassign.domain.entity.AssignmentDecision;
import com.routeassign.domain.entity.AssignmentDecisionCandidate;
import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.UserDetails;
import com.routeassign.domain.enums.AssignmentRuleKey;
import com.routeassign.domain.enums.AssignmentScoreFactor;
import com.routeassign.domain.enums.RejectionReason;
import com.routeassign.dto.response.ScoringResult;
import com.routeassign.repository.AssignmentDecisionCandidateRepository;
import com.routeassign.repository.AssignmentDecisionRepository;
import com.routeassign.repository.DeliveryAssignmentRepository;
import com.routeassign.repository.HistoryDeliveryPartnerRepository;
import com.routeassign.repository.UserDetailsRepository;
import com.routeassign.service.AssignmentRuleService;
import com.routeassign.service.algorithm.AssignmentCandidate;
import com.routeassign.service.algorithm.AssignmentContext;
import com.routeassign.service.algorithm.AssignmentScoringEngine;
import com.routeassign.service.algorithm.DeliveryTimeAlgorithmService;
import com.routeassign.service.algorithm.DistanceAlgorithmService;
import com.routeassign.service.algorithm.EligibilityResult;
import com.routeassign.service.algorithm.PartnerSelectionAlgorithmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Algorithm 4 — Partner Selection Orchestrator (Phase 7).
 *
 * Accepts {@link AssignmentContext} so reassignment attempts can exclude
 * previously failed partners without touching the scoring engine or strategies.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  FLOW                                                                    │
 * ├──────────────────────────────────────────────────────────────────────────┤
 * │  1. Fetch all active + available partners, then subtract excludedIds.    │
 * │     Fetch busy partners if ENABLE_BUSY_PARTNER_REUSE=true.              │
 * │  2. Eligibility check per partner.                                       │
 * │  3. Build AssignmentCandidate for each eligible partner.                 │
 * │  4. Score all eligible candidates.                                       │
 * │  5. Select winner (score → idle time → random).                          │
 * │  6. Return PartnerSelectionResult with full audit payload.               │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerSelectionAlgorithmServiceImpl implements PartnerSelectionAlgorithmService {

    private final UserDetailsRepository                 userDetailsRepository;
    private final DeliveryAssignmentRepository          deliveryAssignmentRepository;
    private final HistoryDeliveryPartnerRepository      historyDeliveryPartnerRepository;
    private final AssignmentDecisionRepository          assignmentDecisionRepository;
    private final AssignmentDecisionCandidateRepository assignmentDecisionCandidateRepository;
    private final DistanceAlgorithmService              distanceAlgorithmService;
    private final DeliveryTimeAlgorithmService          deliveryTimeAlgorithmService;
    private final AssignmentRuleService                 assignmentRuleService;
    private final AssignmentScoringEngine               assignmentScoringEngine;

    private static final double SCORE_EPSILON = 1e-6;

    // ─────────────────────────────────────────────────────────────────────────
    // Primary entry point (AssignmentContext)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Optional<PartnerSelectionResult> selectPartnerWithEta(AssignmentContext context) {
        Order         order       = context.getOrder();
        Set<Long>     excluded    = context.getExcludedPartnerIds();
        int           attempt     = context.getAttemptNumber();

        log.info("Partner selection orderId={} attempt={} excluded={}",
                order.getOrderId(), attempt, excluded);

        LocalDateTime now = LocalDateTime.now();

        // ── Step 1: gather candidate pools, apply exclusion list ──────────────
        List<UserDetails> freeRaw = userDetailsRepository
                .findEligibleDeliveryPartners(order.getTotalWeight())
                .stream()
                .filter(p -> !excluded.contains(p.getId()))
                .toList();

        boolean enableBusyReuse = assignmentRuleService
                .getBooleanRule(AssignmentRuleKey.ENABLE_BUSY_PARTNER_REUSE);

        List<UserDetails> busyRaw = enableBusyReuse
                ? userDetailsRepository.findBusyEligibleDeliveryPartners(order.getTotalWeight())
                        .stream()
                        .filter(p -> !excluded.contains(p.getId()))
                        .toList()
                : List.of();

        Set<Long> sameVendorIds = computeSameVendorIds(freeRaw, order);

        // ── Step 2: eligibility check ─────────────────────────────────────────
        List<EligibilityResult> allEligibility = new ArrayList<>();

        for (UserDetails p : freeRaw) {
            allEligibility.add(checkEligibility(p, order, false));
        }

        Set<Long> freeIds = freeRaw.stream().map(UserDetails::getId).collect(Collectors.toSet());
        for (UserDetails p : busyRaw) {
            if (!freeIds.contains(p.getId())) {
                allEligibility.add(checkEligibility(p, order, true));
            }
        }

        List<EligibilityResult> eligible = allEligibility.stream()
                .filter(EligibilityResult::eligible)
                .toList();

        log.debug("Eligibility: {}/{} eligible (attempt={} orderId={})",
                eligible.size(), allEligibility.size(), attempt, order.getOrderId());

        if (eligible.isEmpty()) {
            log.warn("No eligible partners for orderId={} attempt={}", order.getOrderId(), attempt);
            return Optional.empty();
        }

        // ── Step 3: build candidates ──────────────────────────────────────────
        List<AssignmentCandidate> candidates =
                buildCandidates(eligible, order, now, sameVendorIds);

        // ── Step 4: score all eligible candidates ─────────────────────────────
        Map<AssignmentScoreFactor, Double> weightSnapshot =
                assignmentScoringEngine.getEnabledWeights();

        List<ScoredCandidate> scored = candidates.stream()
                .map(c -> new ScoredCandidate(c, assignmentScoringEngine.score(c)))
                .toList();

        // ── Step 5: select winner ─────────────────────────────────────────────
        ScoredCandidate winner       = selectWinner(scored);
        ScoringResult   winnerResult = winner.scoringResult();

        log.info("Selected partnerId={} isBusy={} ETA={} score={:.2f} orderId={}",
                winner.candidate().getPartnerId(), winner.candidate().isBusy(),
                winner.candidate().getEta(), winnerResult.getFinalScore(), order.getOrderId());

        return Optional.of(new PartnerSelectionResult(
                winner.candidate().getPartner(),
                winner.candidate().getEta(),
                winner.candidate().isBusy(),
                winner.candidate().getWorkStartTime(),
                winnerResult,
                weightSnapshot,
                allEligibility,
                scored
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audit persistence
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void saveDecision(DeliveryAssignment                assignment,
                             ScoringResult                     result,
                             Map<AssignmentScoreFactor, Double>  weightSnapshot,
                             List<EligibilityResult>           allEligibility,
                             List<ScoredCandidate>             scoredCandidates,
                             Long                              winnerPartnerId) {

        AssignmentDecision decision = AssignmentDecision.builder()
                .deliveryAssignment(assignment)
                .distanceScore(result.getDistanceScore())
                .capacityScore(result.getCapacityScore())
                .ratingScore(result.getRatingScore())
                .idleTimeScore(result.getIdleTimeScore())
                .sameVendorScore(result.getSameVendorScore())
                .finalScore(result.getFinalScore())
                .distanceWeight(weightSnapshot.getOrDefault(AssignmentScoreFactor.DISTANCE,    null))
                .capacityWeight(weightSnapshot.getOrDefault(AssignmentScoreFactor.CAPACITY,    null))
                .ratingWeight(  weightSnapshot.getOrDefault(AssignmentScoreFactor.RATING,      null))
                .idleTimeWeight(weightSnapshot.getOrDefault(AssignmentScoreFactor.IDLE_TIME,   null))
                .sameVendorWeight(weightSnapshot.getOrDefault(AssignmentScoreFactor.SAME_VENDOR, null))
                .build();

        decision = assignmentDecisionRepository.save(decision);

        Map<Long, ScoredCandidate> scoredByPartnerId = scoredCandidates.stream()
                .collect(Collectors.toMap(sc -> sc.candidate().getPartnerId(), sc -> sc));

        List<AssignmentDecisionCandidate> rows = new ArrayList<>();
        final AssignmentDecision saved = decision;

        for (EligibilityResult er : allEligibility) {
            ScoredCandidate sc     = scoredByPartnerId.get(er.partnerId());
            boolean         winner = er.partnerId().equals(winnerPartnerId);

            String partnerName = er.partner().getAuth() != null
                    ? er.partner().getAuth().getUsername() : "unknown";

            rows.add(AssignmentDecisionCandidate.builder()
                    .decision(saved)
                    .partnerId(er.partnerId())
                    .partnerName(partnerName)
                    .eligible(er.eligible())
                    .rejectionReason(er.rejectionReason())
                    .distanceScore(  sc != null ? sc.scoringResult().getDistanceScore()   : null)
                    .capacityScore(  sc != null ? sc.scoringResult().getCapacityScore()   : null)
                    .ratingScore(    sc != null ? sc.scoringResult().getRatingScore()      : null)
                    .idleTimeScore(  sc != null ? sc.scoringResult().getIdleTimeScore()    : null)
                    .sameVendorScore(sc != null ? sc.scoringResult().getSameVendorScore()  : null)
                    .finalScore(     sc != null ? sc.scoringResult().getFinalScore()       : null)
                    .isWinner(winner)
                    .build());
        }

        assignmentDecisionCandidateRepository.saveAll(rows);

        log.info("Saved decision id={} with {} candidate rows for assignmentId={}",
                saved.getId(), rows.size(), assignment.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Eligibility
    // ─────────────────────────────────────────────────────────────────────────

    private EligibilityResult checkEligibility(UserDetails partner, Order order, boolean isBusy) {
        if (partner.getLatitude() == null || partner.getLongitude() == null) {
            return EligibilityResult.rejected(partner, RejectionReason.MISSING_LOCATION_DATA);
        }
        double remaining = remainingCapacity(partner);
        if (remaining < order.getTotalWeight()) {
            return EligibilityResult.rejected(partner, RejectionReason.INSUFFICIENT_CAPACITY);
        }
        if (isBusy && !assignmentRuleService.getBooleanRule(AssignmentRuleKey.ENABLE_BUSY_PARTNER_REUSE)) {
            return EligibilityResult.rejected(partner, RejectionReason.PARTNER_UNAVAILABLE);
        }
        return EligibilityResult.eligible(partner);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Candidate building
    // ─────────────────────────────────────────────────────────────────────────

    private List<AssignmentCandidate> buildCandidates(List<EligibilityResult> eligible,
                                                      Order order,
                                                      LocalDateTime now,
                                                      Set<Long> sameVendorIds) {
        double vendorLat   = order.getVendor().getLatitude();
        double vendorLon   = order.getVendor().getLongitude();
        double customerLat = order.getDeliveryLocationLatitude();
        double customerLon = order.getDeliveryLocationLongitude();
        int    restMinutes = assignmentRuleService.getIntegerRule(AssignmentRuleKey.REST_DURATION_MINUTES);

        List<AssignmentCandidate> list = new ArrayList<>();
        for (EligibilityResult er : eligible) {
            UserDetails partner = er.partner();
            boolean     isBusy  = !Boolean.TRUE.equals(partner.getIsAvailable());

            double distToVendor   = distanceAlgorithmService.calculateDistance(
                    partner.getLatitude(), partner.getLongitude(), vendorLat, vendorLon);
            double distToCustomer = distanceAlgorithmService.calculateDistance(
                    vendorLat, vendorLon, customerLat, customerLon);

            LocalDateTime workStart;
            LocalDateTime eta;

            if (isBusy) {
                var latestOpt = deliveryAssignmentRepository
                        .findLatestActiveAssignmentByPartner(partner.getId());
                if (latestOpt.isEmpty() || latestOpt.get().getExpectedDeliveryTime() == null) {
                    isBusy    = false;
                    workStart = deliveryTimeAlgorithmService.determineWorkStartTime(now, distToVendor);
                    eta       = deliveryTimeAlgorithmService.calculateExpectedDeliveryTime(
                            now, distToVendor, distToCustomer);
                } else {
                    LocalDateTime currentEta    = latestOpt.get().getExpectedDeliveryTime();
                    LocalDateTime restReadyTime = currentEta.plusMinutes(restMinutes);
                    workStart = deliveryTimeAlgorithmService.determineWorkStartTime(restReadyTime, distToVendor);
                    eta       = deliveryTimeAlgorithmService.calculateEtaForBusyPartner(
                            currentEta, restMinutes, distToVendor, distToCustomer);
                }
            } else {
                workStart = deliveryTimeAlgorithmService.determineWorkStartTime(now, distToVendor);
                eta       = deliveryTimeAlgorithmService.calculateExpectedDeliveryTime(
                        now, distToVendor, distToCustomer);
            }

            list.add(new AssignmentCandidate(
                    partner, order,
                    distToVendor, distToCustomer,
                    remainingCapacity(partner),
                    idleMinutes(partner.getId(), now),
                    lastCompletedAt(partner.getId()),
                    sameVendorIds.contains(partner.getId()),
                    eta, workStart, isBusy
            ));
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Winner selection
    // ─────────────────────────────────────────────────────────────────────────

    private ScoredCandidate selectWinner(List<ScoredCandidate> scored) {
        if (scored.size() == 1) return scored.get(0);

        double maxScore = scored.stream()
                .mapToDouble(sc -> sc.scoringResult().getFinalScore())
                .max().orElse(0.0);

        List<ScoredCandidate> top = scored.stream()
                .filter(sc -> maxScore - sc.scoringResult().getFinalScore() <= SCORE_EPSILON)
                .collect(Collectors.toList());

        if (top.size() == 1) return top.get(0);

        long maxIdle = top.stream()
                .mapToLong(sc -> sc.candidate().getIdleMinutes())
                .max().orElse(0L);

        List<ScoredCandidate> idleWinners = top.stream()
                .filter(sc -> sc.candidate().getIdleMinutes() == maxIdle)
                .collect(Collectors.toList());

        if (idleWinners.size() == 1) return idleWinners.get(0);

        return idleWinners.get(new Random().nextInt(idleWinners.size()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Same-vendor helper
    // ─────────────────────────────────────────────────────────────────────────

    private Set<Long> computeSameVendorIds(List<UserDetails> freePartners, Order order) {
        Long   vendorId     = order.getVendor().getId();
        double maxExtraDist = assignmentRuleService.getDoubleRule(AssignmentRuleKey.SAME_VENDOR_MAX_DISTANCE);
        double vendorLat    = order.getVendor().getLatitude();
        double vendorLon    = order.getVendor().getLongitude();

        double extraDist = distanceAlgorithmService.calculateDistance(
                vendorLat, vendorLon,
                order.getDeliveryLocationLatitude(),
                order.getDeliveryLocationLongitude());

        if (extraDist > maxExtraDist) return Set.of();

        return freePartners.stream()
                .filter(p -> !deliveryAssignmentRepository
                        .findActiveAssignmentsByPartnerAndVendor(p.getId(), vendorId)
                        .isEmpty())
                .map(UserDetails::getId)
                .collect(Collectors.toSet());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Measurement helpers
    // ─────────────────────────────────────────────────────────────────────────

    private double remainingCapacity(UserDetails partner) {
        double cap      = partner.getCapacity()              != null ? partner.getCapacity()              : 0.0;
        double assigned = partner.getCurrentAssignedWeight() != null ? partner.getCurrentAssignedWeight() : 0.0;
        return Math.max(0.0, cap - assigned);
    }

    private long idleMinutes(Long partnerId, LocalDateTime now) {
        return historyDeliveryPartnerRepository
                .findLatestCompletedByPartner(partnerId)
                .map(h -> Duration.between(h.getCompletedAt(), now).toMinutes())
                .orElse(Long.MAX_VALUE);
    }

    private LocalDateTime lastCompletedAt(Long partnerId) {
        return historyDeliveryPartnerRepository
                .findLatestCompletedByPartner(partnerId)
                .map(h -> h.getCompletedAt())
                .orElse(null);
    }
}
