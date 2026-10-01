package com.routeassign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single entry in the delivery partner leaderboard.
 * Returned as a list by GET /api/users/leaderboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryResponse {

    private int    rank;
    private Long   partnerId;
    private String username;
    private String email;
    private Double rating;
    private long   completedDeliveries;
    private long   cancelledDeliveries;
    private Double currentAssignedWeightKg;
    private Double remainingCapacityKg;
    private Boolean isAvailable;
}
