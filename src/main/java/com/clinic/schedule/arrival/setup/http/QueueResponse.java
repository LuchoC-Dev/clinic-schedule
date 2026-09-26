package com.clinic.schedule.arrival.setup.http;

import java.util.List;
import java.util.UUID;

import com.clinic.schedule.arrival.setup.query.QueueView;

/** A queue as the API returns it: its name, its room — null when it has none — and who serves it. */
record QueueResponse(UUID id, String name, String room, List<UUID> practitionerIds) {

    static QueueResponse of(QueueView queue) {
        return new QueueResponse(queue.id(), queue.name(), queue.room(), queue.practitioners());
    }
}
