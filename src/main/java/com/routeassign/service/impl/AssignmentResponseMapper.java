package com.routeassign.service.impl;

import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.dto.response.DeliveryAssignmentResponse;

import java.time.LocalDateTime;

/**
 * Stateless utility that maps a {@link DeliveryAssignment} entity to
 * a {@link DeliveryAssignmentResponse} DTO.
 *
 * Previously this mapping was duplicated in both
 * {@link AssignmentLifecycleServiceImpl} and {@link DeliveryAssignmentServiceImpl}.
 * Both classes now call this single mapper, ensuring consistent field handling.
 *
 * The class is intentionally package-private — callers inside this package
 * use it via static methods; no Spring injection is needed since there is no
 * state or dependency.
 */
final class AssignmentResponseMapper {

    private AssignmentResponseMapper() {}

    /**
     * Maps a persisted {@link DeliveryAssignment} to the response DTO.
     *
     * {@code isBusy} and {@code workStartTime} are only meaningful at assignment
     * creation time (they come from the algorithm result). For all other reads
     * (status updates, history queries) pass {@code false} and {@code null}.
     *
     * @param da            the persisted assignment
     * @param isBusy        true if the partner was mid-delivery when assigned
     * @param workStartTime when the partner will begin travelling (null if not applicable)
     */
    static DeliveryAssignmentResponse toResponse(DeliveryAssignment da,
                                                  boolean isBusy,
                                                  LocalDateTime workStartTime) {
        double p2v = da.getDistancePartnerToVendor()  != null ? da.getDistancePartnerToVendor()  : 0.0;
        double v2c = da.getDistanceVendorToCustomer() != null ? da.getDistanceVendorToCustomer() : 0.0;

        return DeliveryAssignmentResponse.builder()
                .id(da.getId())
                .orderId(da.getOrder().getOrderId())
                .deliveryPartnerId(da.getDeliveryPartner().getId())
                .deliveryPartnerName(da.getDeliveryPartner().getAuth().getUsername())
                .vendorId(da.getVendor().getId())
                .vendorName(da.getVendor().getAuth().getUsername())
                .assignedAt(da.getAssignedAt())
                .expectedDeliveryTime(da.getExpectedDeliveryTime())
                .deliveryStatus(da.getDeliveryStatus())
                .distancePartnerToVendor(p2v)
                .distanceVendorToCustomer(v2c)
                .totalDistance(p2v + v2c)
                .isPartnerBusy(isBusy)
                .workStartTime(workStartTime)
                .build();
    }

    /**
     * Convenience overload for contexts where busy/workStart information is
     * not available (status updates, lifecycle transitions, query responses).
     */
    static DeliveryAssignmentResponse toResponse(DeliveryAssignment da) {
        return toResponse(da, false, null);
    }
}
