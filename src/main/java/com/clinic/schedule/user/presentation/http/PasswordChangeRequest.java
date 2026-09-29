package com.clinic.schedule.user.presentation.http;

import jakarta.validation.constraints.NotBlank;

/** The body for changing one's own password (D-69). */
record PasswordChangeRequest(
        @NotBlank(message = "the current password is required")
        String currentPassword,

        @NotBlank(message = "the new password is required")
        String newPassword) {
}
