package com.clinic.schedule.arrival.setup.domain;

import java.util.UUID;

/** Identity of a queue, which is also the id of its stream of events (D-19, D-62). */
public record QueueId(UUID value) {

    public QueueId {
        if (value == null) {
            throw new IllegalArgumentException("A queue needs an id");
        }
    }

    public static QueueId newId() {
        return new QueueId(UUID.randomUUID());
    }
}
