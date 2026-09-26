package com.clinic.schedule.arrival.setup.command;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.arrival.eventstore.EventStore;
import com.clinic.schedule.arrival.eventstore.NewEvent;
import com.clinic.schedule.arrival.eventstore.StoredEvent;
import com.clinic.schedule.arrival.setup.domain.Queue;
import com.clinic.schedule.arrival.setup.domain.QueueEvent;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerJoined;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerLeft;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueCreated;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueRenamed;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A queue's setup as its stream of events (D-62): read back into the {@link Queue} it describes,
 * and added to. Only the commands use it — the reads go to the projection — so it is theirs.
 *
 * <p>It is where the events become text and back. Each has a fixed name in storage, chosen here,
 * so that renaming a class never orphans what was already recorded.
 */
@Repository
class QueueEventRepository {

    private static final Map<Class<? extends QueueEvent>, String> NAMES = Map.of(
            QueueCreated.class, "QueueCreated",
            QueueRenamed.class, "QueueRenamed",
            PractitionerJoined.class, "PractitionerJoined",
            PractitionerLeft.class, "PractitionerLeft");

    private static final Map<String, Class<? extends QueueEvent>> TYPES = Map.of(
            "QueueCreated", QueueCreated.class,
            "QueueRenamed", QueueRenamed.class,
            "PractitionerJoined", PractitionerJoined.class,
            "PractitionerLeft", PractitionerLeft.class);

    private final EventStore store;
    private final ObjectMapper json;

    QueueEventRepository(EventStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    /** The queue, or empty when no queue has that id. */
    Optional<Queue> find(QueueId id) {
        List<StoredEvent> stored = store.load(id.value());
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Queue.replay(id, stored.stream().map(this::read).toList()));
    }

    /**
     * @param expectedVersion how many events the queue had when it was read: 0 for a new one
     * @throws com.clinic.schedule.arrival.eventstore.StreamChangedException when it changed meanwhile
     */
    void append(QueueId id, int expectedVersion, List<QueueEvent> events, LocalDateTime at, String by) {
        store.append(id.value(), expectedVersion, events.stream().map(this::write).toList(), at, by);
    }

    private NewEvent write(QueueEvent event) {
        try {
            return new NewEvent(NAMES.get(event.getClass()), json.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A queue event could not be written", e);
        }
    }

    private QueueEvent read(StoredEvent stored) {
        Class<? extends QueueEvent> type = TYPES.get(stored.type());
        if (type == null) {
            throw new IllegalStateException("Unknown queue event " + stored.type() + " in " + stored.streamId());
        }
        try {
            return json.readValue(stored.payload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A queue event could not be read: " + stored.streamId(), e);
        }
    }
}
