package com.routeassign.dto.response;

import com.routeassign.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload for an Order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long orderId;
    private Long customerId;
    private String customerName;
    private Long vendorId;
    private String vendorName;
    private OrderStatus orderStatus;
    private LocalDateTime createdAt;
    private Double deliveryLocationLatitude;
    private Double deliveryLocationLongitude;
    private Double totalWeight;
    private List<OrderItemResponse> items;
}
