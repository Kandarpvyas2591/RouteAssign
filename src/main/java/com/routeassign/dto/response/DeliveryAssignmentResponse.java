package com.routeassign.dto.response;

import com.routeassign.domain.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response payload for a Delivery Assignment.
 * Returned after the auto-assignment algorithm selects a delivery partner.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAssignmentResponse {

    private Long id;
    private Long orderId;
    private Long deliveryPartnerId;
    private String deliveryPartnerName;
    private Long vendorId;
    private String vendorName;
    private LocalDateTime assignedAt;
    private LocalDateTime expectedDeliveryTime;
    private DeliveryStatus deliveryStatus;
    private Double distancePartnerToVendor;
    private Double distanceVendorToCustomer;
    private Double totalDistance;
}
