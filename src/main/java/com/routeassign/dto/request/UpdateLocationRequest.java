package com.routeassign.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request payload for updating the home location of a delivery partner or vendor.
 */
@Data
public class UpdateLocationRequest {

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;
}
