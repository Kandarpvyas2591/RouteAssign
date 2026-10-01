package com.routeassign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A delivery partner's personal dashboard snapshot.
 * Returned by GET /api/users/{id}/dashboard
 *
 * Shows current workload, capacity, live assignments, and career totals
 * — everything a partner needs on a single screen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerDashboardResponse {

    // ── Identity ──────────────────────────────────────────────────────────────
    private Long   id;
    private String username;
    private String email;
    private String mobileNo;
    private Double latitude;
    private Double longitude;

    // ── Live status ───────────────────────────────────────────────────────────
    private Boolean isAvailable;
    private Boolean isActive;

    // ── Capacity snapshot ─────────────────────────────────────────────────────
    private Double capacityKg;
    private Double currentAssignedWeightKg;
    private Double remainingCapacityKg;

    // ── Rating ────────────────────────────────────────────────────────────────
    private Double rating;

    // ── Active assignments (non-terminal) ─────────────────────────────────────
    /** All currently active (non-terminal) assignments for this partner. */
    private List<DeliveryAssignmentResponse> activeAssignments;
    private int activeAssignmentCount;

    // ── Career totals ─────────────────────────────────────────────────────────
    private long totalDeliveries;
    private long completedDeliveries;
    private long cancelledDeliveries;
    private long failedDeliveries;
}
