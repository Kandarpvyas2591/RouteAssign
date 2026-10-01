package com.routeassign.service;

import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.CustomerDetailsResponse;

import java.util.List;

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
     * Returns all customers in the system — used for the admin customer list.
     */
    List<CustomerDetailsResponse> getAll();

    /**
     * Updates the default delivery location of a customer.
     */
    CustomerDetailsResponse updateLocation(Long id, UpdateLocationRequest request);

    /**
     * Soft-deactivates a customer account. Sets isActive = false on the
     * linked UserAuth record so the account cannot log in.
     */
    void deactivate(Long id);
}
