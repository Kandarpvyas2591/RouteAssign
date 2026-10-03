package com.routeassign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * System-wide statistics for the Admin Dashboard.
 * Returned by GET /api/dashboard/stats
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    // ── User counts ───────────────────────────────────────────────────────────
    private long totalCustomers;
    private long totalVendors;
    private long activeVendors;
    private long totalDeliveryPartners;
    private long activeDeliveryPartners;
    private long availableDeliveryPartners;   // active + isAvailable=true

    // ── Order counts ──────────────────────────────────────────────────────────
    private long totalOrders;
    private long pendingOrders;
    private long assignedOrders;
    private long inProgressOrders;            // PICKED_UP + IN_TRANSIT combined
    private long deliveredOrders;
    private long cancelledOrders;

    // ── Live delivery health ──────────────────────────────────────────────────
    private long liveAssignments;             // non-terminal assignments right now
    private long assignedAssignments;         // status = ASSIGNED (awaiting acceptance)
    private long acceptedAssignments;         // status = ACCEPTED  (travelling to vendor)
    private long pickedUpAssignments;         // status = PICKED_UP (at vendor, collected)
    private long inTransitAssignments;        // status = IN_TRANSIT (en route to customer)

    // ── Capacity snapshot ─────────────────────────────────────────────────────
    private double totalSystemCapacityKg;     // sum of all active partner capacity
    private double usedSystemCapacityKg;      // sum of currentAssignedWeight
    private double freeSystemCapacityKg;      // difference
}
