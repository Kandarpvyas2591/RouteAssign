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
 *
 * When {@code isPartnerBusy = true} the selected partner was on another delivery
 * at assignment time. The client can display:
 *   - {@code workStartTime}  — when the partner will be rested and start travelling
 *   - {@code expectedDeliveryTime} — final ETA after rest + travel
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

    /**
     * True when the assigned partner was busy on another (different-vendor) delivery
     * at the time this order was placed. Their ETA includes a rest period.
     */
    private Boolean isPartnerBusy;

    /**
     * The time from which the partner will begin travelling for this order.
     *
     * For a FREE partner  : the normal work-start time (now, or 10 AM next day, etc.)
     * For a BUSY partner  : currentDeliveryEta + restDurationMinutes,
     *                       snapped to the next valid working window.
     *
     * Null only for legacy records created before this field was introduced.
     */
    private LocalDateTime workStartTime;
}
