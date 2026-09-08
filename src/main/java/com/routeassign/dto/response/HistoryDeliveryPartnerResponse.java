package com.routeassign.dto.response;

import com.routeassign.domain.enums.HistoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response payload for a delivery partner history record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryDeliveryPartnerResponse {

    private Long id;
    private Long orderId;
    private Long deliveryPartnerId;
    private String deliveryPartnerName;
    private Double rating;
    private HistoryStatus status;
    private LocalDateTime completedAt;
    private Boolean isActive;
}
