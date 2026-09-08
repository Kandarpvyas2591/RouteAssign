package com.routeassign.service;

import com.routeassign.dto.request.StoreRequest;
import com.routeassign.dto.response.StoreResponse;

import java.util.List;

public interface StoreService {

    /**
     * Adds a new item (or updates quantity if already present) to a vendor's store.
     */
    StoreResponse addOrUpdateStock(Long vendorId, StoreRequest request);

    /**
     * Returns a single store entry by its ID.
     */
    StoreResponse getById(Long id);

    /**
     * Returns all store entries for a given vendor.
     */
    List<StoreResponse> getByVendorId(Long vendorId);

    /**
     * Removes a stock entry from a vendor's store.
     */
    void removeStock(Long vendorId, Long itemId);
}
