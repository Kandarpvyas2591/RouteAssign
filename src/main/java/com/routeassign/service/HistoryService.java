package com.routeassign.service;

import com.routeassign.dto.request.RatingRequest;
import com.routeassign.dto.response.HistoryDeliveryPartnerResponse;
import com.routeassign.dto.response.HistoryVendorResponse;

import java.util.List;

public interface HistoryService {

    // ── Delivery Partner History ──────────────────────────────────────────────

    /**
     * Returns the full delivery history for a specific partner.
     */
    List<HistoryDeliveryPartnerResponse> getPartnerHistory(Long partnerId);

    /**
     * Returns a single delivery partner history record by its ID.
     */
    HistoryDeliveryPartnerResponse getPartnerHistoryById(Long historyId);

    /**
     * Submits a rating for a completed delivery by the customer.
     * Also updates the partner's aggregated rating in UserDetails.
     */
    HistoryDeliveryPartnerResponse rateDelivery(Long historyId, RatingRequest request);

    // ── Vendor History ────────────────────────────────────────────────────────

    /**
     * Returns the full order history for a specific vendor.
     */
    List<HistoryVendorResponse> getVendorHistory(Long vendorId);

    /**
     * Returns a single vendor history record by its ID.
     */
    HistoryVendorResponse getVendorHistoryById(Long historyId);

    /**
     * Submits a rating for a vendor for a completed order.
     * Also updates the vendor's aggregated rating in VendorDetails.
     */
    HistoryVendorResponse rateVendor(Long historyId, RatingRequest request);
}
