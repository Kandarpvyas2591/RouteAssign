package com.routeassign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for a Store (vendor inventory entry).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreResponse {

    private Long id;
    private Long vendorId;
    private String vendorName;
    private ItemResponse item;
    private Integer quantity;
}
