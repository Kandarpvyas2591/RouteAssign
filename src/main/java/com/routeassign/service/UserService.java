package com.routeassign.service;

import com.routeassign.dto.request.UpdateAvailabilityRequest;
import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.LeaderboardEntryResponse;
import com.routeassign.dto.response.PartnerDashboardResponse;
import com.routeassign.dto.response.UserDetailsResponse;

import java.util.List;

public interface UserService {

    /**
     * Returns the profile of a user (delivery partner) by their UserDetails ID.
     */
    UserDetailsResponse getById(Long id);

    /**
     * Returns the profile of a user by their UserAuth ID.
     */
    UserDetailsResponse getByAuthId(Long authId);

    /**
     * Returns all active delivery partners.
     */
    List<UserDetailsResponse> getAllActiveDeliveryPartners();

    /**
     * Updates the home latitude/longitude of a delivery partner.
     */
    UserDetailsResponse updateLocation(Long id, UpdateLocationRequest request);

    /**
     * Toggles the availability flag of a delivery partner.
     */
    UserDetailsResponse updateAvailability(Long id, UpdateAvailabilityRequest request);

    /**
     * Soft-deletes (deactivates) a user account.
     */
    void deactivate(Long id);

    /**
     * Returns a partner's personal dashboard: live workload, capacity snapshot,
     * active assignments, and career delivery totals.
     * Intended for GET /api/users/{id}/dashboard
     */
    PartnerDashboardResponse getPartnerDashboard(Long id);

    /**
     * Returns the delivery partner leaderboard sorted by rating descending,
     * then by completed deliveries descending.
     * Only active partners are included.
     * {@code limit} caps the number of entries (0 or negative means return all).
     */
    List<LeaderboardEntryResponse> getLeaderboard(int limit);
}
