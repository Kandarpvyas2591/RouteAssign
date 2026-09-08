package com.routeassign.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request payload for a delivery partner to toggle their availability.
 */
@Data
public class UpdateAvailabilityRequest {

    @NotNull(message = "Availability flag is required")
    private Boolean isAvailable;
}
