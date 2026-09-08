package com.routeassign.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request payload for placing a new order.
 */
@Data
public class OrderRequest {

    @NotNull(message = "Vendor ID is required")
    private Long vendorId;

    @NotNull(message = "Delivery latitude is required")
    private Double deliveryLocationLatitude;

    @NotNull(message = "Delivery longitude is required")
    private Double deliveryLocationLongitude;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;
}
