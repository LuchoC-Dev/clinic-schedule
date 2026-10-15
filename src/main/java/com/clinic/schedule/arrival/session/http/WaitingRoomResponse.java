package com.clinic.schedule.arrival.session.http;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.query.SessionView;
import com.clinic.schedule.arrival.setup.query.QueueView;

/**
 * A queue as the waiting room shows it (D-109): its name and room, whether it is open, the names of whom its
 * practitioners called and are seeing, and of who waits, in the order they will be called. Nothing else of a patient:
 * this type has no place for it.
 *
 * @param room null when the queue has none
 */
record WaitingRoomResponse(UUID id, String name, String room, boolean open, List<String> called, List<String> waiting) {

    static WaitingRoomResponse of(QueueView queue, Optional<SessionView> session, Map<UUID, String> names) {
        return new WaitingRoomResponse(queue.id(), queue.name(), queue.room(), session.isPresent(),
                session.map(open -> namesOf(open.called(), names)).orElse(List.of()),
                session.map(open -> namesOf(open.waiting(), names)).orElse(List.of()));
    }

    private static List<String> namesOf(List<Entry> entries, Map<UUID, String> names) {
        return entries.stream().map(entry -> names.getOrDefault(entry.patientId(), "(paciente desconocido)")).toList();
    }
}
