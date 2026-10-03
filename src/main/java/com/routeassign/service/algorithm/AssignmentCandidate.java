package com.routeassign.service.algorithm;

import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.UserDetails;
import com.routeassign.service.algorithm.strategy.CandidateContext;

import java.time.LocalDateTime;

/**
 * Represents a single eligible partner candidate evaluated for a specific order.
 *
 * This is the single object that flows through every stage of the assignment
 * pipeline after the eligibility check passes:
 *
 * <pre>
 * Eligibility check  →  AssignmentCandidate  →  ScoringEngine  →  Winner selection
 * </pre>
 *
 * It wraps all pre-computed measurements so no service needs to recalculate them:
 * <ul>
 *   <li>Distance legs (partner→vendor, vendor→customer, total)</li>
 *   <li>Remaining capacity</li>
 *   <li>Idle time (minutes since last completed delivery)</li>
 *   <li>Same-vendor flag</li>
 *   <li>ETA and work-start time (from DeliveryTimeAlgorithmService)</li>
 *   <li>isBusy flag (true = cross-vendor busy-partner reuse path)</li>
 * </ul>
 *
 * The {@link CandidateContext} is derived from this object and passed to the
 * scoring strategies — strategies receive only what they need, while the
 * full candidate remains available to the orchestrator.
 */
public class AssignmentCandidate {

    // ── Core references ───────────────────────────────────────────────────────

    private final UserDetails   partner;
    private final Order         order;

    // ── Distance (km) ─────────────────────────────────────────────────────────

    /** Distance from the partner's home to the vendor (km). */
    private final double distanceToVendorKm;

    /** Distance from the vendor to the customer delivery location (km). */
    private final double distanceVendorToCustomerKm;

    /** Total route distance: distanceToVendorKm + distanceVendorToCustomerKm. */
    private final double totalDistanceKm;

    // ── Capacity (kg) ─────────────────────────────────────────────────────────

    /** Partner's remaining carrying capacity (capacity − currentAssignedWeight). */
    private final double remainingCapacityKg;

    // ── Partner profile ───────────────────────────────────────────────────────

    /** Partner's aggregate rating; 0.0 if unrated (null → 0.0). */
    private final double partnerRating;

    // ── Activity ──────────────────────────────────────────────────────────────

    /**
     * Minutes since the partner's last completed delivery.
     * {@link Long#MAX_VALUE} for a partner who has never been assigned.
     */
    private final long idleMinutes;

    /**
     * Timestamp of the partner's last completed delivery; null if never assigned.
     * Stored for audit purposes in {@code AssignmentDecisionCandidate}.
     */
    private final LocalDateTime lastCompletedAt;

    // ── Vendor context ────────────────────────────────────────────────────────

    /**
     * True if the partner already has an active assignment at the same vendor
     * as this order. Enables the same-vendor route-consolidation bonus.
     */
    private final boolean sameVendor;

    // ── Scheduling ────────────────────────────────────────────────────────────

    /** Pre-computed expected delivery time for this candidate. */
    private final LocalDateTime eta;

    /** When the partner will begin travelling (after rest period for busy partners). */
    private final LocalDateTime workStartTime;

    /**
     * True when this candidate is currently mid-delivery on a different vendor's
     * order (cross-vendor busy-partner reuse path).
     */
    private final boolean isBusy;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AssignmentCandidate(UserDetails   partner,
                               Order         order,
                               double        distanceToVendorKm,
                               double        distanceVendorToCustomerKm,
                               double        remainingCapacityKg,
                               long          idleMinutes,
                               LocalDateTime lastCompletedAt,
                               boolean       sameVendor,
                               LocalDateTime eta,
                               LocalDateTime workStartTime,
                               boolean       isBusy) {
        this.partner                   = partner;
        this.order                     = order;
        this.distanceToVendorKm        = distanceToVendorKm;
        this.distanceVendorToCustomerKm = distanceVendorToCustomerKm;
        this.totalDistanceKm           = distanceToVendorKm + distanceVendorToCustomerKm;
        this.remainingCapacityKg       = remainingCapacityKg;
        this.partnerRating             = partner.getRating() != null ? partner.getRating() : 0.0;
        this.idleMinutes               = idleMinutes;
        this.lastCompletedAt           = lastCompletedAt;
        this.sameVendor                = sameVendor;
        this.eta                       = eta;
        this.workStartTime             = workStartTime;
        this.isBusy                    = isBusy;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UserDetails   getPartner()                    { return partner; }
    public Long          getPartnerId()                  { return partner.getId(); }
    public Order         getOrder()                      { return order; }
    public double        getDistanceToVendorKm()         { return distanceToVendorKm; }
    public double        getDistanceVendorToCustomerKm() { return distanceVendorToCustomerKm; }
    public double        getTotalDistanceKm()            { return totalDistanceKm; }
    public double        getRemainingCapacityKg()        { return remainingCapacityKg; }
    public double        getPartnerRating()              { return partnerRating; }
    public long          getIdleMinutes()                { return idleMinutes; }
    public LocalDateTime getLastCompletedAt()            { return lastCompletedAt; }
    public boolean       isSameVendor()                  { return sameVendor; }
    public LocalDateTime getEta()                        { return eta; }
    public LocalDateTime getWorkStartTime()              { return workStartTime; }
    public boolean       isBusy()                        { return isBusy; }

    /**
     * Produces the {@link CandidateContext} required by scoring strategies.
     * The context is a lightweight projection of this candidate's measurements;
     * strategies receive exactly what they need and nothing more.
     */
    public CandidateContext toContext() {
        return new CandidateContext(
                partner,
                order,
                distanceToVendorKm,
                distanceVendorToCustomerKm,
                totalDistanceKm,
                remainingCapacityKg,
                idleMinutes,
                sameVendor
        );
    }

    @Override
    public String toString() {
        return "AssignmentCandidate{partnerId=%d, dist=%.1fkm, cap=%.1fkg, idle=%dmin, sameVendor=%b, busy=%b, eta=%s}"
                .formatted(partner.getId(), totalDistanceKm, remainingCapacityKg,
                           idleMinutes == Long.MAX_VALUE ? -1L : idleMinutes,
                           sameVendor, isBusy, eta);
    }
}
