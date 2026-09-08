package com.routeassign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for a single order line item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private Long id;
    private Long itemId;
    private String itemName;
    private Integer quantity;
    private Double weight;
    private Double lineWeight;  // quantity * weight
}
