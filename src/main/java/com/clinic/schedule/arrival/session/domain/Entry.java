package com.clinic.schedule.arrival.session.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A patient's place in a session (D-63, D-64): who, since when, whether they are an emergency and
 * since when, their appointment if they came with one, whom they are kept for, where they stand,
 * how often they were called, who has them now, and at which call they are passed over after not
 * coming in. Immutable; a session changes by making new ones.
 *
 * @param sequence the order they came in, which settles two arrivals at the same time
 * @param emergencySince null when they are not an emergency
 * @param appointmentId null when they came without an appointment
 * @param appointmentAt the appointment's time; null without one
 * @param reservedFor the only practitioner who may call them; null when any of the queue may
 * @param withPractitioner who called or saw them; null while they wait
 * @param passedOverAtCall the session's call at which they are passed over; 0 for none
 */
public record Entry(EntryId id, UUID patientId, int sequence, LocalDateTime arrivedAt, LocalDateTime emergencySince,
        UUID appointmentId, LocalDateTime appointmentAt, UUID reservedFor, EntryState state, int timesCalled,
        UUID withPractitioner, LocalDateTime lastCalledAt, int passedOverAtCall) {

    public Entry {
        if (id == null || patientId == null || arrivedAt == null || state == null) {
            throw new IllegalArgumentException("An entry needs an id, a patient, its arrival and its state");
        }
        if ((appointmentId == null) != (appointmentAt == null)) {
            throw new IllegalArgumentException("An appointment comes with its time");
        }
    }

    static Entry arrived(EntryId id, UUID patientId, int sequence, boolean emergency, LocalDateTime at) {
        return new Entry(id, patientId, sequence, at, emergency ? at : null, null, null, null, EntryState.WAITING, 0,
                null, null, 0);
    }

    static Entry arrivedWithAppointment(EntryId id, UUID patientId, int sequence, UUID appointmentId,
            UUID practitionerId, LocalDateTime appointmentAt, LocalDateTime at) {
        return new Entry(id, patientId, sequence, at, null, appointmentId, appointmentAt, practitionerId,
                EntryState.WAITING, 0, null, null, 0);
    }

    public boolean isWaiting() {
        return state == EntryState.WAITING;
    }

    public boolean isEmergency() {
        return emergencySince != null;
    }

    public boolean hasAppointment() {
        return appointmentId != null;
    }

    /** Whether that practitioner may call them, as far as their reservation goes. */
    public boolean isCallableBy(UUID practitionerId) {
        return reservedFor == null || reservedFor.equals(practitionerId);
    }

    /**
     * When they count from in the order: since they became an emergency; for an appointment, the
     * later of its time and their arrival, so whoever comes early waits for their time and whoever
     * comes late is placed by when they came; otherwise when they arrived.
     */
    public LocalDateTime placedAt() {
        if (isEmergency()) {
            return emergencySince;
        }
        if (hasAppointment()) {
            return appointmentAt.isAfter(arrivedAt) ? appointmentAt : arrivedAt;
        }
        return arrivedAt;
    }

    Entry asEmergency(LocalDateTime at) {
        return new Entry(id, patientId, sequence, arrivedAt, at, appointmentId, appointmentAt, reservedFor, state,
                timesCalled, withPractitioner, lastCalledAt, passedOverAtCall);
    }

    Entry reservedFor(UUID practitionerId) {
        return new Entry(id, patientId, sequence, arrivedAt, emergencySince, appointmentId, appointmentAt,
                practitionerId, state, timesCalled, withPractitioner, lastCalledAt, passedOverAtCall);
    }

    Entry calledBy(UUID practitionerId, LocalDateTime at) {
        return new Entry(id, patientId, sequence, arrivedAt, emergencySince, appointmentId, appointmentAt, reservedFor,
                EntryState.CALLED, timesCalled + 1, practitionerId, at, 0);
    }

    Entry in(EntryState newState) {
        UUID keeps = newState == EntryState.SEEN || newState == EntryState.CALLED ? withPractitioner : null;
        return new Entry(id, patientId, sequence, arrivedAt, emergencySince, appointmentId, appointmentAt, reservedFor,
                newState, timesCalled, keeps, lastCalledAt, passedOverAtCall);
    }

    Entry passedOverAt(int call) {
        return new Entry(id, patientId, sequence, arrivedAt, emergencySince, appointmentId, appointmentAt, reservedFor,
                EntryState.WAITING, timesCalled, null, lastCalledAt, call);
    }
}
