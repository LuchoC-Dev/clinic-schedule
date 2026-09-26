package com.clinic.schedule.arrival.setup.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads the queues from the tables the projection keeps, never from the events: those belong to
 * the commands (D-62). Package-private, a detail of the reads.
 */
@Repository
class QueueViewReader {

    private final JdbcTemplate jdbc;

    QueueViewReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every queue, by name. */
    List<QueueView> findAll() {
        return read("", new Object[0]);
    }

    Optional<QueueView> find(UUID id) {
        return read(" where q.id = ?", new Object[] {id}).stream().findFirst();
    }

    private List<QueueView> read(String where, Object[] arguments) {
        Map<UUID, Building> queues = new LinkedHashMap<>();
        jdbc.query("select q.id, q.name, q.room, m.practitioner_id from arrival_queue q "
                        + "left join queue_membership m on m.queue_id = q.id" + where + " order by q.name_key",
                row -> {
                    String name = row.getString("name");
                    String room = row.getString("room");
                    Building queue = queues.computeIfAbsent(row.getObject("id", UUID.class),
                            id -> new Building(id, name, room));
                    UUID practitioner = row.getObject("practitioner_id", UUID.class);
                    if (practitioner != null) {
                        queue.practitioners.add(practitioner);
                    }
                }, arguments);
        return queues.values().stream()
                .map(queue -> new QueueView(queue.id, queue.name, queue.room, List.copyOf(queue.practitioners)))
                .toList();
    }

    private record Building(UUID id, String name, String room, List<UUID> practitioners) {
        Building(UUID id, String name, String room) {
            this(id, name, room, new ArrayList<>());
        }
    }
}
