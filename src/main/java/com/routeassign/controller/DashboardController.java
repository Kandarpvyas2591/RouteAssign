package com.routeassign.controller;

import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.DashboardStatsResponse;
import com.routeassign.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/stats
     *
     * Returns a single system-wide snapshot for the admin dashboard UI:
     *   - User counts (customers, vendors, delivery partners, available partners)
     *   - Order counts by every status
     *   - Live delivery counts by every assignment status
     *   - Capacity utilisation (total / used / free kg across all active partners)
     *
     * Response: 200 OK — DashboardStatsResponse
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getStats()));
    }
}
