package com.clinic.schedule.arrival.session.domain;

import java.util.UUID;

/** Identity of a queue session — one opening of a queue — and of its stream of events (D-63). */
public record SessionId(UUID value) {

    public SessionId {
        if (value == null) {
            throw new IllegalArgumentException("A session needs an id");
        }
    }

    public static SessionId newId() {
        return new SessionId(UUID.randomUUID());
    }
}
