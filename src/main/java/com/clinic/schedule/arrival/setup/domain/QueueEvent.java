package com.clinic.schedule.arrival.setup.domain;

import java.util.UUID;

/**
 * What can happen to a queue's setup (D-62). The queue is these events, in order: its state is
 * what they add up to, and nothing else is kept. Each one is a fact, named in the past, and is
 * never changed once recorded.
 *
 * <p>Plain values, so they can be written as they are and read back; who recorded each and when
 * is kept beside it by the event store.
 */
public sealed interface QueueEvent {

    /** A queue was set up, with its name and, when it has one, its consulting room. */
    record QueueCreated(String name, String room) implements QueueEvent {
    }

    /** Its name or its room changed; both are given, as they are now. */
    record QueueRenamed(String name, String room) implements QueueEvent {
    }

    /** A practitioner started serving it. */
    record PractitionerJoined(UUID practitionerId) implements QueueEvent {
    }

    /** A practitioner stopped serving it. */
    record PractitionerLeft(UUID practitionerId) implements QueueEvent {
    }
}
