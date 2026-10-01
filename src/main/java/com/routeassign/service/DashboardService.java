package com.routeassign.service;

import com.routeassign.dto.response.DashboardStatsResponse;

/**
 * Provides aggregated system-wide statistics for the Admin Dashboard.
 */
public interface DashboardService {

    /**
     * Returns a full system snapshot — user counts, order counts, live delivery
     * statuses, and capacity utilisation — in a single call.
     *
     * Intended for GET /api/dashboard/stats
     */
    DashboardStatsResponse getStats();
}
