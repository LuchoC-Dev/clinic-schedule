package com.clinic.schedule.practitioner.presentation.http;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body for adding a reason to a practitioner, or changing one; a new one is never booked on the web. */
record ReasonRequest(
        @NotBlank(message = "the name is required")
        @Size(max = 60, message = "the name is at most 60 characters")
        String name,

        @Min(value = 1, message = "the duration must be at least one minute")
        int durationMinutes,

        /** Whether patients may book it on the web (D-82); left out, they may not. */
        boolean webBookable) {
}
