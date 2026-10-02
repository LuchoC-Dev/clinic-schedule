package com.clinic.schedule.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One of a patient's appointments as the patient reads it on the web (D-80): with whom, when, for what
 * reason and how it stands, and — when it was cancelled — whether the patient or the practice asked.
 *
 * <p>It has no place for the appointment's note nor for who cancelled it: those are reception's (D-76), and
 * leaving them out of the type is what keeps them from reaching the patient.
 *
 * @param cancellationRequestedBy null unless it was cancelled with its cancellation recorded
 */
public record PatientAppointment(UUID id, UUID practitionerId, LocalDateTime startsAt, String reason, State state,
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
