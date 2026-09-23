package com.clinic.schedule.practitioner.api;

import java.util.UUID;

/** A reason as other modules see it: its name and how long it takes with this practitioner. */
public record ReasonDetails(UUID id, String name, int durationMinutes) {
}
