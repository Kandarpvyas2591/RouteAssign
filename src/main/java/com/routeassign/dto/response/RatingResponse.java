package com.routeassign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for a Rating record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingResponse {

    private Long ratingId;
    private Double rating;
}
