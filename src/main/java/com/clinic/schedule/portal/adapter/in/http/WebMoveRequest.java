package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

/** The body a patient sends to move one of their appointments on the web to another time (D-93). */
record WebMoveRequest(
        @NotNull(message = "the time is required")
        LocalDateTime startsAt) {
}
