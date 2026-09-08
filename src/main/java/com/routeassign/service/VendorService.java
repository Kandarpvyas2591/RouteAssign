package com.routeassign.service;

import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.VendorDetailsResponse;

import java.util.List;

public interface VendorService {

    /**
     * Returns a vendor by VendorDetails ID.
     */
    VendorDetailsResponse getById(Long id);

    /**
     * Returns a vendor by their UserAuth ID.
     */
    VendorDetailsResponse getByAuthId(Long authId);

    /**
     * Returns all active vendors.
     */
    List<VendorDetailsResponse> getAllActive();

    /**
     * Updates the physical location of the vendor (used in distance calculations).
     */
    VendorDetailsResponse updateLocation(Long id, UpdateLocationRequest request);

    /**
     * Soft-deletes (deactivates) a vendor.
     */
    void deactivate(Long id);
}
