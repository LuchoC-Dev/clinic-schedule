package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * The body a patient sends to book an appointment on the web (D-82): for themselves, or for a member of their
 * family they act for, named by {@code patientId} (D-85).
 */
record WebBookingRequest(
        @NotNull(message = "the practitioner is required")
        UUID practitionerId,

        @NotNull(message = "the reason is required")
        UUID reasonId,

        @NotNull(message = "the time is required")
        LocalDateTime startsAt,
        UUID patientId) {
}
