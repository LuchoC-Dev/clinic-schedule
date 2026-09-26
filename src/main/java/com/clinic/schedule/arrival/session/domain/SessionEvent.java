package com.clinic.schedule.arrival.session.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What can happen in a queue session, from its opening to its closing (D-63). The session is these
 * events, in order. The ones the order of the queue depends on carry their time, so the domain
 * needs nothing but the events; who recorded each, and when, is kept beside it by the event store.
 */
public sealed interface SessionEvent {

    /** Reception opened the queue: the session starts. */
    record SessionOpened(UUID queueId, LocalDateTime at) implements SessionEvent {
    }

    /** Reception brought a patient in, as an emergency or not. */
    record PatientArrived(UUID entryId, UUID patientId, boolean emergency, LocalDateTime at) implements SessionEvent {
    }

    /** Reception made a waiting patient an emergency. */
    record EmergencyDeclared(UUID entryId, LocalDateTime at) implements SessionEvent {
    }

    /** A practitioner called the patient. */
    record PatientCalled(UUID entryId, UUID practitionerId, LocalDateTime at) implements SessionEvent {
    }

    /** The practitioner who called them finished seeing them. */
    record PatientSeen(UUID entryId) implements SessionEvent {
    }

    /** Called, they did not come in: passed over at the next call, and waiting again. */
    record PatientDidNotComeIn(UUID entryId) implements SessionEvent {
    }

    /** Called for the last time without coming in: out of the queue. */
    record PatientDidNotShowUp(UUID entryId) implements SessionEvent {
    }

    /** Reception says the waiting patient left without being seen. */
    record PatientLeft(UUID entryId) implements SessionEvent {
    }

    /** Reception keeps the queue open past the end of its day. */
    record AutoCloseTurnedOff() implements SessionEvent {
    }

    /** The queue closed, by hand or at the end of its day: whoever was waiting was not seen. */
    record SessionClosed(LocalDateTime at) implements SessionEvent {
    }
}
