package com.clinic.schedule.arrival.session.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.arrival.api.QueueArrival;
import com.clinic.schedule.arrival.session.domain.CallOrder;
import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.EntryState;

/**
 * Reads the sessions from the tables the projection keeps, never from the events (D-63). The order
 * of those waiting is the domain's {@link CallOrder}, so it is decided in one place. Package-private,
 * a detail of the reads.
 */
@Repository
class SessionViewReader {

    private final JdbcTemplate jdbc;

    SessionViewReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<SessionView> openSessionOf(UUID queueId) {
        return jdbc.query("select s.id, s.queue_id, s.opened_at, s.auto_close from queue_session s "
                        + "join open_queue_session o on o.session_id = s.id where o.queue_id = ?",
                (row, number) -> view(row.getObject("id", UUID.class), row.getObject("queue_id", UUID.class),
                        row.getTimestamp("opened_at").toLocalDateTime(), row.getBoolean("auto_close")),
                queueId).stream().findFirst();
    }

    List<QueueArrival> arrivalsOf(UUID patientId, LocalDate day) {
        return jdbc.query("select s.queue_id, e.state, e.with_practitioner, e.reserved_for from session_entry e "
                        + "join queue_session s on s.id = e.session_id "
                        + "where e.patient_id = ? and (e.state = 'WAITING' "
                        + "or (e.with_practitioner is not null and e.last_called_at >= ? and e.last_called_at < ?))",
                (row, number) -> {
                    UUID queueId = row.getObject("queue_id", UUID.class);
                    return new QueueArrival(queueId, servedBy(queueId), "WAITING".equals(row.getString("state")),
                            row.getObject("reserved_for", UUID.class), row.getObject("with_practitioner", UUID.class));
                },
                patientId, Timestamp.valueOf(day.atStartOfDay()), Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
    }

    /** The first arrival with each appointment, from the queues or from before them (D-65). */
    Map<UUID, LocalDateTime> appointmentArrivals(Collection<UUID> appointmentIds) {
        Map<UUID, LocalDateTime> arrivals = new HashMap<>();
        if (appointmentIds.isEmpty()) {
            return arrivals;
        }
        String ids = String.join(", ", Collections.nCopies(appointmentIds.size(), "?"));
        Object[] arguments = appointmentIds.toArray();
        RowCallbackHandler keepFirst = row -> arrivals.merge(row.getObject("appointment_id", UUID.class),
                row.getTimestamp("arrived_at").toLocalDateTime(), (one, other) -> one.isBefore(other) ? one : other);
        jdbc.query("select appointment_id, arrived_at from session_entry where appointment_id in (" + ids + ")",
                keepFirst, arguments);
        jdbc.query("select appointment_id, checked_in_at as arrived_at from appointment_check_in "
                + "where appointment_id in (" + ids + ")", keepFirst, arguments);
        return arrivals;
    }

    List<UUID> servedBy(UUID queueId) {
        return jdbc.queryForList("select practitioner_id from queue_membership where queue_id = ?", UUID.class, queueId);
    }

    private SessionView view(UUID sessionId, UUID queueId, LocalDateTime openedAt, boolean closesByItself) {
        List<Entry> entries = jdbc.query("select * from session_entry where session_id = ? order by sequence",
                (row, number) -> entry(row), sessionId);
        return new SessionView(sessionId, queueId, openedAt, closesByItself, CallOrder.of(entries),
                CallOrder.uncallable(entries, servedBy(queueId)),
                entries.stream().filter(entry -> entry.state() == EntryState.CALLED).toList(),
                entries.stream().filter(entry -> !entry.isWaiting() && entry.state() != EntryState.CALLED).toList());
    }

    private static Entry entry(ResultSet row) throws SQLException {
        return new Entry(new EntryId(row.getObject("id", UUID.class)), row.getObject("patient_id", UUID.class),
                row.getInt("sequence"), row.getTimestamp("arrived_at").toLocalDateTime(),
                time(row.getTimestamp("emergency_since")), row.getObject("appointment_id", UUID.class),
                time(row.getTimestamp("appointment_at")), row.getObject("reserved_for", UUID.class),
                EntryState.valueOf(row.getString("state")),
                row.getInt("times_called"), row.getObject("with_practitioner", UUID.class),
                time(row.getTimestamp("last_called_at")), row.getInt("passed_over_at_call"));
    }

    private static LocalDateTime time(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
