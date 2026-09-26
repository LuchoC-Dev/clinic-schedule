package com.clinic.schedule.arrival.session.domain;

import java.util.UUID;

/** Identity of a patient's place in a session: the same patient coming in twice is two entries (D-63). */
public record EntryId(UUID value) {

    public EntryId {
        if (value == null) {
            throw new IllegalArgumentException("An entry needs an id");
        }
    }

    public static EntryId newId() {
        return new EntryId(UUID.randomUUID());
    }
}
