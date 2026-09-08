package com.routeassign.service;

import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.CustomerDetailsResponse;

public interface CustomerService {

    /**
     * Returns the customer profile by CustomerDetails ID.
     */
    CustomerDetailsResponse getById(Long id);

    /**
     * Returns the customer profile by their UserAuth ID.
     */
    CustomerDetailsResponse getByAuthId(Long authId);

    /**
     * Updates the default delivery location of a customer.
     */
    CustomerDetailsResponse updateLocation(Long id, UpdateLocationRequest request);
}
