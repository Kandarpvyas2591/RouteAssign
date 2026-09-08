package com.routeassign.dto.response;

import com.routeassign.domain.enums.HistoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for a vendor history record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryVendorResponse {

    private Long id;
    private Long orderId;
    private Long vendorId;
    private String vendorName;
    private Double rating;
    private HistoryStatus status;
    private Boolean isActive;
}
