package com.routeassign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for vendor profile details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorDetailsResponse {

    private Long id;
    private Long authId;
    private String username;
    private String email;
    private Double latitude;
    private Double longitude;
    private Double rating;
    private Boolean isActive;
}
