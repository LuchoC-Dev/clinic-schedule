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

    private static final Map<String, Class<? extends SessionEvent>> TYPES = Map.ofEntries(
            Map.entry("SessionOpened", SessionEvent.SessionOpened.class),
            Map.entry("PatientArrived", SessionEvent.PatientArrived.class),
            Map.entry("AppointmentArrived", SessionEvent.AppointmentArrived.class),
            Map.entry("PatientReassigned", SessionEvent.PatientReassigned.class),
            Map.entry("ReservationLifted", SessionEvent.ReservationLifted.class),
            Map.entry("EmergencyDeclared", SessionEvent.EmergencyDeclared.class),
            Map.entry("PatientCalled", SessionEvent.PatientCalled.class),
            Map.entry("PatientSeen", SessionEvent.PatientSeen.class),
            Map.entry("PatientDidNotComeIn", SessionEvent.PatientDidNotComeIn.class),
            Map.entry("PatientDidNotShowUp", SessionEvent.PatientDidNotShowUp.class),
            Map.entry("PatientLeft", SessionEvent.PatientLeft.class),
            Map.entry("AutoCloseTurnedOff", SessionEvent.AutoCloseTurnedOff.class),
            Map.entry("SessionClosed", SessionEvent.SessionClosed.class));

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
