package com.routeassign.controller;

import com.routeassign.dto.request.RatingRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.HistoryDeliveryPartnerResponse;
import com.routeassign.dto.response.HistoryVendorResponse;
import com.routeassign.service.HistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    // ── Delivery Partner History ──────────────────────────────────────────────

    /**
     * GET /api/history/partners/{partnerId}
     * Get the full delivery history for a partner.
     */
    @GetMapping("/partners/{partnerId}")
    public ResponseEntity<ApiResponse<List<HistoryDeliveryPartnerResponse>>> getPartnerHistory(
            @PathVariable Long partnerId) {
        return ResponseEntity.ok(ApiResponse.success(historyService.getPartnerHistory(partnerId)));
    }

    /**
     * GET /api/history/partners/records/{historyId}
     * Get a single delivery partner history record.
     */
    @GetMapping("/partners/records/{historyId}")
    public ResponseEntity<ApiResponse<HistoryDeliveryPartnerResponse>> getPartnerHistoryById(
            @PathVariable Long historyId) {
        return ResponseEntity.ok(ApiResponse.success(historyService.getPartnerHistoryById(historyId)));
    }

    /**
     * POST /api/history/partners/records/{historyId}/rate
     * Submit a rating for a completed delivery.
     */
    @PostMapping("/partners/records/{historyId}/rate")
    public ResponseEntity<ApiResponse<HistoryDeliveryPartnerResponse>> rateDelivery(
            @PathVariable Long historyId,
            @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Delivery rated", historyService.rateDelivery(historyId, request)));
    }

    // ── Vendor History ────────────────────────────────────────────────────────

    /**
     * GET /api/history/vendors/{vendorId}
     * Get the full order history for a vendor.
     */
    @GetMapping("/vendors/{vendorId}")
    public ResponseEntity<ApiResponse<List<HistoryVendorResponse>>> getVendorHistory(
            @PathVariable Long vendorId) {
        return ResponseEntity.ok(ApiResponse.success(historyService.getVendorHistory(vendorId)));
    }

    /**
     * GET /api/history/vendors/records/{historyId}
     * Get a single vendor history record.
     */
    @GetMapping("/vendors/records/{historyId}")
    public ResponseEntity<ApiResponse<HistoryVendorResponse>> getVendorHistoryById(
            @PathVariable Long historyId) {
        return ResponseEntity.ok(ApiResponse.success(historyService.getVendorHistoryById(historyId)));
    }

    /**
     * POST /api/history/vendors/records/{historyId}/rate
     * Submit a rating for a vendor after order completion.
     */
    @PostMapping("/vendors/records/{historyId}/rate")
    public ResponseEntity<ApiResponse<HistoryVendorResponse>> rateVendor(
            @PathVariable Long historyId,
            @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Vendor rated", historyService.rateVendor(historyId, request)));
    }
}
