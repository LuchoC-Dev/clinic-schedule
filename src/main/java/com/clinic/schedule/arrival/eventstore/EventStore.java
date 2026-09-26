package com.clinic.schedule.arrival.eventstore;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Where the module's events are kept (D-62): each stream — one queue's setup today — is the list
 * of what happened to it, in order, and nothing in it is ever changed or deleted.
 *
 * <p>It knows streams, versions and payloads, not what they mean: turning events into text and back
 * is the business of whoever owns the stream. Plain JDBC over portable SQL: an append-only table
 * with a composite key gains nothing from an entity.
 *
 * <p>Two writers of the same stream at once are told apart by the key: each event takes the next
 * version, so the second to append the same one is refused, and nothing it wrote remains.
 */
@Repository
public class EventStore {

    private final JdbcTemplate jdbc;

    EventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every event of the stream, in the order they happened; empty for a stream that does not exist. */
    public List<StoredEvent> load(UUID streamId) {
        return jdbc.query("select stream_id, version, event_type, payload, recorded_at, recorded_by "
                        + "from arrival_event where stream_id = ? order by version",
                (row, number) -> new StoredEvent(row.getObject("stream_id", UUID.class), row.getInt("version"),
                        row.getString("event_type"), row.getString("payload"),
                        row.getTimestamp("recorded_at").toLocalDateTime(), row.getString("recorded_by")),
                streamId);
    }

    /**
     * Adds events at the end of the stream, which must still be at {@code expectedVersion} — the
     * number of events it had when it was read, 0 for a new one.
     *
     * @throws StreamChangedException when someone else appended to it in the meantime
     */
    public void append(UUID streamId, int expectedVersion, List<NewEvent> events, LocalDateTime recordedAt,
            String recordedBy) {
        int version = expectedVersion;
        try {
            for (NewEvent event : events) {
                version++;
                jdbc.update("insert into arrival_event (stream_id, version, event_type, payload, recorded_at, recorded_by) "
                                + "values (?, ?, ?, ?, ?, ?)",
                        streamId, version, event.type(), event.payload(), Timestamp.valueOf(recordedAt), recordedBy);
            }
        } catch (DuplicateKeyException e) {
            throw new StreamChangedException(streamId);
        }
    }
}
