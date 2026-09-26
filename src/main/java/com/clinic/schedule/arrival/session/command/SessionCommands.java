package com.clinic.schedule.arrival.session.command;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.SessionEvent;
import com.clinic.schedule.arrival.session.domain.SessionId;
import com.clinic.schedule.arrival.session.projection.SessionProjection;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.projection.QueueProjection;
import com.clinic.schedule.arrival.setup.query.FindQueueUseCase;
import com.clinic.schedule.security.api.CurrentUserReader;

/**
 * What every command on a session shares (D-63): finding the session — a queue's open one, an
 * entry's, a practitioner's — and, once decided, appending the events stamped with the clock and
 * who did it, and bringing the projection up to date, in the caller's transaction. Package-private:
 * a detail of the commands.
 */
@Component
class SessionCommands {

    /** Who records what nobody did by hand: a queue closing at the end of its day. */
    static final String SYSTEM = "system";

    private final SessionEventRepository events;
    private final SessionProjection projection;
    private final QueueProjection queues;
    private final FindQueueUseCase findQueue;
    private final CurrentUserReader currentUser;
    private final Clock clock;

    SessionCommands(SessionEventRepository events, SessionProjection projection, QueueProjection queues,
            FindQueueUseCase findQueue, CurrentUserReader currentUser, Clock clock) {
        this.events = events;
        this.projection = projection;
        this.queues = queues;
        this.findQueue = findQueue;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** The queue's open session's id, if it has one. */
    Optional<SessionId> openSessionIdOf(UUID queueId) {
        return projection.openSessionOf(queueId);
    }

    /** @throws com.clinic.schedule.arrival.setup.QueueNotFoundException when no queue has that id */
    void requireQueue(UUID queueId) {
        findQueue.handle(new QueueId(queueId));
    }

    /** @throws QueueNotOpenException when the queue has no open session */
    QueueSession openSessionOf(UUID queueId) {
        return projection.openSessionOf(queueId).flatMap(events::find)
                .orElseThrow(QueueNotOpenException::new);
    }

    /** @throws EntryNotFoundException when no session has that entry */
    QueueSession sessionOf(EntryId entryId) {
        return projection.sessionOf(entryId).flatMap(events::find)
                .orElseThrow(() -> new EntryNotFoundException(entryId));
    }

    QueueSession find(SessionId id) {
        return events.find(id).orElseThrow(() -> new IllegalStateException("A session in the projection has no events: " + id.value()));
    }

    /** The practitioner the user stands for. */
    UUID currentPractitioner() {
        return currentUser.practitionerId().orElseThrow(NotAPractitionerException::new);
    }

    /** The queue the practitioner serves. @throws NoQueueServedException when they serve none */
    UUID queueServedBy(UUID practitionerId) {
        return queues.queueServedBy(practitionerId).map(QueueId::value).orElseThrow(NoQueueServedException::new);
    }

    /** The queue the practitioner serves, if any. */
    Optional<UUID> queueOf(UUID practitionerId) {
        return queues.queueServedBy(practitionerId).map(QueueId::value);
    }

    /** Who serves the queue now. */
    List<UUID> servedBy(UUID queueId) {
        return findQueue.handle(new QueueId(queueId)).practitioners();
    }

    void record(QueueSession session, SessionEvent event) {
        record(session, List.of(event), currentUser.username());
    }

    /** Records the events, by whoever is using the application; nothing when there are none. */
    void recordAll(QueueSession session, List<SessionEvent> recorded) {
        record(session, recorded, currentUser.username());
    }

    void recordAsSystem(QueueSession session, List<SessionEvent> recorded) {
        record(session, recorded, SYSTEM);
    }

    /** Opens a session: its first event makes it. */
    SessionId open(SessionEvent.SessionOpened opened) {
        SessionId id = SessionId.newId();
        events.append(id, 0, List.of(opened), now(), currentUser.username());
        projection.save(QueueSession.replay(id, List.of(opened)), true);
        return id;
    }

    private void record(QueueSession session, List<SessionEvent> recorded, String by) {
        if (recorded.isEmpty()) {
            return;
        }
        events.append(session.id(), session.version(), recorded, now(), by);
        QueueSession after = session;
        for (SessionEvent event : recorded) {
            after = after.apply(event);
        }
        projection.save(after, false);
    }
}
