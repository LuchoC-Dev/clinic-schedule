package com.clinic.schedule.arrival.session.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.arrival.session.domain.SessionEvent.AutoCloseTurnedOff;
import com.clinic.schedule.arrival.session.domain.SessionEvent.EmergencyDeclared;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientArrived;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientCalled;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientLeft;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientSeen;
import com.clinic.schedule.arrival.session.domain.SessionEvent.SessionClosed;
import com.clinic.schedule.arrival.session.domain.SessionEvent.SessionOpened;
import com.clinic.schedule.arrival.session.domain.exception.NotServingThisQueueException;
import com.clinic.schedule.arrival.session.domain.exception.QueueAlreadyOpenException;

/**
 * The only way into a queue session (D-17, D-63). It knows what a session alone cannot, and is
 * given it rather than reading it: whether the queue already has an open session, who serves the
 * queue, and how many calls a patient gets before they are taken out — a setting.
 */
public class QueueSessionService {

    private final int callsBeforeNoShow;

    public QueueSessionService(int callsBeforeNoShow) {
        if (callsBeforeNoShow < 1) {
            throw new IllegalArgumentException("A patient is called at least once");
        }
        this.callsBeforeNoShow = callsBeforeNoShow;
    }

    /** @param openSession the queue's session still open, if any */
    public SessionOpened open(UUID queueId, Optional<SessionId> openSession, LocalDateTime at) {
        if (queueId == null || at == null) {
            throw new IllegalArgumentException("A session needs its queue and the time it opens");
        }
        if (openSession.isPresent()) {
            throw new QueueAlreadyOpenException();
        }
        return new SessionOpened(queueId, at);
    }

    public PatientArrived arrive(QueueSession session, UUID patientId, boolean emergency, LocalDateTime at) {
        return session.arrive(patientId, emergency, at);
    }

    public EmergencyDeclared declareEmergency(QueueSession session, EntryId entryId, LocalDateTime at) {
        return session.declareEmergency(entryId, at);
    }

    public PatientLeft leave(QueueSession session, EntryId entryId) {
        return session.leave(entryId);
    }

    /** @param servedBy who serves the queue now */
    public PatientCalled callNext(QueueSession session, UUID practitionerId, Collection<UUID> servedBy,
            LocalDateTime at) {
        requireServing(practitionerId, servedBy);
        return session.callNext(practitionerId, at);
    }

    public PatientSeen finish(QueueSession session, UUID practitionerId) {
        return session.finish(practitionerId);
    }

    public SessionEvent didNotComeIn(QueueSession session, UUID practitionerId) {
        return session.didNotComeIn(practitionerId, callsBeforeNoShow);
    }

    public AutoCloseTurnedOff turnOffAutoClose(QueueSession session) {
        return session.turnOffAutoClose();
    }

    public SessionClosed close(QueueSession session, LocalDateTime at) {
        return session.close(at);
    }

    /** The events to record, one after the other, when a session's day is over; none when it is not. */
    public List<SessionEvent> closeIfDue(QueueSession session, LocalDateTime now) {
        return session.isDueToClose(now) ? List.of(session.close(now)) : List.of();
    }

    private static void requireServing(UUID practitionerId, Collection<UUID> servedBy) {
        if (practitionerId == null || !servedBy.contains(practitionerId)) {
            throw new NotServingThisQueueException();
        }
    }
}
