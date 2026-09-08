package com.routeassign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for customer profile details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailsResponse {

    private Long id;
    private Long authId;
    private String username;
    private String email;
    private String mobileNo;
    private Double latitude;
    private Double longitude;
}
