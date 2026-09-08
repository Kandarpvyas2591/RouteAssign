package com.routeassign.dto.response;

import com.routeassign.domain.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Profile response for a user / delivery partner.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsResponse {

    private Long id;
    private Long authId;
    private String username;
    private String email;
    private UserRole role;
    private String mobileNo;
    private Double latitude;
    private Double longitude;
    private Boolean isActive;
    private Boolean isAvailable;
    private Double capacity;
    private Double currentAssignedWeight;
    private Double rating;
}
