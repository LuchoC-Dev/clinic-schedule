package com.clinic.schedule.arrival.session.command;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.arrival.eventstore.EventStore;
import com.clinic.schedule.arrival.eventstore.NewEvent;
import com.clinic.schedule.arrival.eventstore.StoredEvent;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.SessionEvent;
import com.clinic.schedule.arrival.session.domain.SessionId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A queue session as its stream of events (D-63), read back into the {@link QueueSession} it
 * describes and added to. Only the commands use it. Each event has a fixed name in storage, chosen
 * here, so renaming a class never orphans what was recorded.
 */
@Repository
class SessionEventRepository {

    private static final Map<String, Class<? extends SessionEvent>> TYPES = Map.of(
            "SessionOpened", SessionEvent.SessionOpened.class,
            "PatientArrived", SessionEvent.PatientArrived.class,
            "EmergencyDeclared", SessionEvent.EmergencyDeclared.class,
            "PatientCalled", SessionEvent.PatientCalled.class,
            "PatientSeen", SessionEvent.PatientSeen.class,
            "PatientDidNotComeIn", SessionEvent.PatientDidNotComeIn.class,
            "PatientDidNotShowUp", SessionEvent.PatientDidNotShowUp.class,
            "PatientLeft", SessionEvent.PatientLeft.class,
            "AutoCloseTurnedOff", SessionEvent.AutoCloseTurnedOff.class,
            "SessionClosed", SessionEvent.SessionClosed.class);

    private static final Map<Class<? extends SessionEvent>, String> NAMES = TYPES.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private final EventStore store;
    private final ObjectMapper json;

    SessionEventRepository(EventStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    Optional<QueueSession> find(SessionId id) {
        List<StoredEvent> stored = store.load(id.value());
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(QueueSession.replay(id, stored.stream().map(this::read).toList()));
    }

    void append(SessionId id, int expectedVersion, List<SessionEvent> events, LocalDateTime at, String by) {
        store.append(id.value(), expectedVersion, events.stream().map(this::write).toList(), at, by);
    }

    private NewEvent write(SessionEvent event) {
        try {
            return new NewEvent(NAMES.get(event.getClass()), json.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A session event could not be written", e);
        }
    }

    private SessionEvent read(StoredEvent stored) {
        Class<? extends SessionEvent> type = TYPES.get(stored.type());
        if (type == null) {
            throw new IllegalStateException("Unknown session event " + stored.type() + " in " + stored.streamId());
        }
        try {
            return json.readValue(stored.payload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A session event could not be read: " + stored.streamId(), e);
        }
    }
}
