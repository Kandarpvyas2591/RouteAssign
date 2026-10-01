package com.routeassign.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for PATCH /api/auth/password
 * The caller must supply their current password for verification
 * before the new password is accepted.
 */
@Data
public class ChangePasswordRequest {

    @NotNull(message = "Auth user ID is required")
    private Long authUserId;

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;
}
