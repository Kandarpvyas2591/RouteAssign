package com.routeassign.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for PUT /api/v1/assignment-rules/{key}
 *
 * <pre>
 * {
 *   "value": "40"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRuleRequest {

    @NotBlank(message = "value must not be blank")
    @Size(max = 255, message = "value must not exceed 255 characters")
    private String value;
}
