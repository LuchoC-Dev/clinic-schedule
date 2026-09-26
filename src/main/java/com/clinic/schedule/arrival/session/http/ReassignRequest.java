package com.clinic.schedule.arrival.session.http;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The practitioner of the queue a waiting patient is given to (D-64). */
record ReassignRequest(@NotNull(message = "the practitioner is required") UUID practitionerId) {
}
