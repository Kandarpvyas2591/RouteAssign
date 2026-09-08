package com.routeassign.service;

import com.routeassign.dto.request.UpdateAvailabilityRequest;
import com.routeassign.dto.request.UpdateLocationRequest;
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
}
