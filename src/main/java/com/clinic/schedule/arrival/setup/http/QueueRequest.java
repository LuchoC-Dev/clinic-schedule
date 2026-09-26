package com.clinic.schedule.arrival.setup.http;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body for setting up a queue or renaming it; the room is optional (D-62). */
record QueueRequest(
        @NotBlank(message = "the name is required")
        @Size(max = 60, message = "the name is at most 60 characters")
        String name,

        @Size(max = 20, message = "the consulting room is at most 20 characters")
        String room) {
}
