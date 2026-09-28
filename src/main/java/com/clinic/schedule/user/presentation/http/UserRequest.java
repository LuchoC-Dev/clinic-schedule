package com.clinic.schedule.user.presentation.http;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.clinic.schedule.user.api.Role;

/**
 * The body for creating a user (D-68). The practitioner is sent for a practitioner's user alone;
 * what the username and password must be is checked by the module, which says why when it refuses.
 */
record UserRequest(
        @NotBlank(message = "the username is required")
        String username,

        @NotNull(message = "the role is required")
        Role role,

        UUID practitionerId,

        @NotBlank(message = "the password is required")
        String password) {
}
