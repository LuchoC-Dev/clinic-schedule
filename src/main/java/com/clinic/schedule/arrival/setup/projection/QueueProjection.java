package com.clinic.schedule.arrival.setup.projection;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.arrival.setup.domain.QueueEvent;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerJoined;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerLeft;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueCreated;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueRenamed;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.domain.exception.PractitionerInAnotherQueueException;
import com.clinic.schedule.arrival.setup.domain.exception.QueueNameTakenException;

/**
 * What the queue events add up to, as tables: every queue with its name and room, and which queue
 * each practitioner serves (D-62). It is written by the commands in the same transaction as the
 * events, so it never lags behind them, and it is what the reads list.
 *
 * <p>It also guards the two rules that span queues. {@code QueueSetupService} checks them against
 * what this answers, and the unique keys here refuse what two commands at once could still let
 * through: a failure rolls the events back with it.
 */
@Repository
public class QueueProjection {

    private final JdbcTemplate jdbc;

    QueueProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void apply(QueueId id, List<QueueEvent> events) {
        for (QueueEvent event : events) {
            switch (event) {
                case QueueCreated created -> nameTaken(created.name(), () -> jdbc.update(
                        "insert into arrival_queue (id, name, name_key, room) values (?, ?, ?, ?)",
                        id.value(), created.name(), key(created.name()), created.room()));
                case QueueRenamed renamed -> nameTaken(renamed.name(), () -> jdbc.update(
                        "update arrival_queue set name = ?, name_key = ?, room = ? where id = ?",
                        renamed.name(), key(renamed.name()), renamed.room(), id.value()));
                case PractitionerJoined joined -> {
                    try {
                        jdbc.update("insert into queue_membership (practitioner_id, queue_id) values (?, ?)",
                                joined.practitionerId(), id.value());
                    } catch (DuplicateKeyException e) {
                        throw new PractitionerInAnotherQueueException();
                    }
                }
                case PractitionerLeft left -> jdbc.update(
                        "delete from queue_membership where practitioner_id = ? and queue_id = ?",
                        left.practitionerId(), id.value());
            }
        }
    }

    /** The names of every queue but that one; every queue's when it is empty. */
    public List<String> namesOtherThan(Optional<QueueId> queue) {
        return jdbc.queryForList("select name from arrival_queue where id <> ?", String.class,
                queue.map(QueueId::value).orElse(new UUID(0, 0)));
    }

    /** The queue the practitioner serves, if any. */
    public Optional<QueueId> queueServedBy(UUID practitionerId) {
        return jdbc.queryForList("select queue_id from queue_membership where practitioner_id = ?", UUID.class,
                        practitionerId).stream()
                .findFirst()
                .map(QueueId::new);
    }

    /** Names differing only in case are the same name. */
    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static void nameTaken(String name, Runnable write) {
        try {
            write.run();
        } catch (DuplicateKeyException e) {
            throw new QueueNameTakenException(name);
        }
    }
}
