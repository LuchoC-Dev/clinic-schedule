package com.clinic.schedule.practitioner.presentation.http;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body for registering or renaming a practitioner. */
record PractitionerRequest(
        @NotBlank(message = "the full name is required")
        @Size(max = 150, message = "the full name is at most 150 characters")
        String fullName) {
}
