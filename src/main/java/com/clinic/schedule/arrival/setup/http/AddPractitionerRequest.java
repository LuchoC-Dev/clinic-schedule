package com.clinic.schedule.arrival.setup.http;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The practitioner who starts serving a queue. */
record AddPractitionerRequest(@NotNull(message = "the practitioner is required") UUID practitionerId) {
}
