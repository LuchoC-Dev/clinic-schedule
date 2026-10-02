package com.clinic.schedule.practitioner.api;

import java.util.UUID;

/**
 * A reason as other modules see it: its name, how long it takes with this practitioner, and whether a
 * patient may book it on the web (D-82).
 */
public record ReasonDetails(UUID id, String name, int durationMinutes, boolean webBookable) {
}
