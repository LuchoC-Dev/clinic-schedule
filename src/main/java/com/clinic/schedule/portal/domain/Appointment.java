package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One of a patient's appointments, as they read it on the web (D-80): with whom, when, for what reason and
 * how it stands, and whether they or the practice asked for it to be cancelled. Never the note nor who
 * cancelled it, which are reception's.
 *
 * @param cancellationRequestedBy null unless it was cancelled, with its cancellation recorded (D-76)
 */
public record Appointment(UUID id, UUID practitionerId, LocalDateTime startsAt, String reason, State state,
        Requester cancellationRequestedBy) {

    public enum State {
        BOOKED,
        CANCELLED,
        ABSENT
    }

    public enum Requester {
        PATIENT,
        PRACTICE
    }
}
