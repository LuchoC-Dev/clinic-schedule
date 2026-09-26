package com.clinic.schedule.arrival.session.projection;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.SessionId;
import com.clinic.schedule.arrival.session.domain.exception.QueueAlreadyOpenException;

/**
 * What a session's events add up to, as tables (D-63): the session, whether it is open, and every
 * patient's place in it. Written by the commands in the same transaction as the events, as the
 * session stands after them — a session is a few dozen rows, so it is written whole rather than
 * event by event, and can never drift from what the events say.
 *
 * <p>Its key on the open session of each queue refuses a second opening that two commands at once
 * could let through.
 */
@Repository
public class SessionProjection {

    private final JdbcTemplate jdbc;

    SessionProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param opened whether these events opened it: a new session */
    public void save(QueueSession session, boolean opened) {
        UUID id = session.id().value();
        if (opened) {
            jdbc.update("insert into queue_session (id, queue_id, opened_at, closed_at, auto_close, calls) "
                            + "values (?, ?, ?, null, ?, ?)",
                    id, session.queueId(), Timestamp.valueOf(session.openedAt()), session.closesByItself(),
                    session.calls());
            try {
                jdbc.update("insert into open_queue_session (queue_id, session_id) values (?, ?)", session.queueId(), id);
            } catch (DuplicateKeyException e) {
                throw new QueueAlreadyOpenException();
            }
        } else {
            jdbc.update("update queue_session set closed_at = ?, auto_close = ?, calls = ? where id = ?",
                    timestamp(session.closedAt()), session.closesByItself(), session.calls(), id);
        }
        if (!session.isOpen()) {
            jdbc.update("delete from open_queue_session where session_id = ?", id);
        }
        jdbc.update("delete from session_entry where session_id = ?", id);
        for (Entry entry : session.entries()) {
            jdbc.update("insert into session_entry (id, session_id, sequence, patient_id, arrived_at, emergency_since, "
                            + "appointment_id, appointment_at, reserved_for, state, times_called, with_practitioner, "
                            + "last_called_at, passed_over_at_call) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    entry.id().value(), id, entry.sequence(), entry.patientId(), Timestamp.valueOf(entry.arrivedAt()),
                    timestamp(entry.emergencySince()), entry.appointmentId(), timestamp(entry.appointmentAt()),
                    entry.reservedFor(), entry.state().name(), entry.timesCalled(), entry.withPractitioner(),
                    timestamp(entry.lastCalledAt()), entry.passedOverAtCall());
        }
    }

    /** The queue's session still open, if any. */
    public Optional<SessionId> openSessionOf(UUID queueId) {
        return jdbc.queryForList("select session_id from open_queue_session where queue_id = ?", UUID.class, queueId)
                .stream().findFirst().map(SessionId::new);
    }

    /** The session the entry belongs to, if the entry exists. */
    public Optional<SessionId> sessionOf(EntryId entryId) {
        return jdbc.queryForList("select session_id from session_entry where id = ?", UUID.class, entryId.value())
                .stream().findFirst().map(SessionId::new);
    }

    /** The open session where that appointment's patient came in, if any. */
    public Optional<SessionId> openSessionWith(UUID appointmentId) {
        return jdbc.queryForList("select e.session_id from session_entry e join open_queue_session o "
                        + "on o.session_id = e.session_id where e.appointment_id = ?", UUID.class, appointmentId)
                .stream().findFirst().map(SessionId::new);
    }

    /** Every open session that still closes by itself: the candidates at the end of a day. */
    public List<SessionId> openClosingByThemselves() {
        return jdbc.queryForList("select s.id from queue_session s join open_queue_session o on o.session_id = s.id "
                        + "where s.auto_close = true", UUID.class)
                .stream().map(SessionId::new).toList();
    }

    private static Timestamp timestamp(LocalDateTime at) {
        return at == null ? null : Timestamp.valueOf(at);
    }
}
