package com.clinic.schedule.arrival.session.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A patient's place in a session (D-63): who, since when, whether they are an emergency and since
 * when, where they stand, how often they were called, who has them now, and at which call they are
 * passed over after not coming in. Immutable; a session changes by making new ones.
 *
 * @param sequence the order they came in, which settles two arrivals at the same time
 * @param emergencySince null when they are not an emergency
 * @param withPractitioner who called or saw them; null while they wait
 * @param passedOverAtCall the session's call at which they are passed over; 0 for none
 */
public record Entry(EntryId id, UUID patientId, int sequence, LocalDateTime arrivedAt, LocalDateTime emergencySince,
        EntryState state, int timesCalled, UUID withPractitioner, LocalDateTime lastCalledAt, int passedOverAtCall) {

    public Entry {
        if (id == null || patientId == null || arrivedAt == null || state == null) {
            throw new IllegalArgumentException("An entry needs an id, a patient, its arrival and its state");
        }
    }

    static Entry arrived(EntryId id, UUID patientId, int sequence, boolean emergency, LocalDateTime at) {
        return new Entry(id, patientId, sequence, at, emergency ? at : null, EntryState.WAITING, 0, null, null, 0);
    }

    public boolean isWaiting() {
        return state == EntryState.WAITING;
    }

    public boolean isEmergency() {
        return emergencySince != null;
    }

    Entry asEmergency(LocalDateTime at) {
        return new Entry(id, patientId, sequence, arrivedAt, at, state, timesCalled, withPractitioner, lastCalledAt,
                passedOverAtCall);
    }

    Entry calledBy(UUID practitionerId, LocalDateTime at) {
        return new Entry(id, patientId, sequence, arrivedAt, emergencySince, EntryState.CALLED, timesCalled + 1,
                practitionerId, at, 0);
    }

    Entry in(EntryState newState) {
        UUID keeps = newState == EntryState.SEEN || newState == EntryState.CALLED ? withPractitioner : null;
        return new Entry(id, patientId, sequence, arrivedAt, emergencySince, newState, timesCalled, keeps, lastCalledAt,
                passedOverAtCall);
    }

    Entry passedOverAt(int call) {
        return new Entry(id, patientId, sequence, arrivedAt, emergencySince, EntryState.WAITING, timesCalled, null,
                lastCalledAt, call);
    }
}
