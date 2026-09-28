package com.clinic.schedule.user.presentation.http;

import jakarta.validation.constraints.NotBlank;

/** The body for resetting a user's password (D-68). */
record PasswordResetRequest(
        @NotBlank(message = "the password is required")
        String password) {
}
