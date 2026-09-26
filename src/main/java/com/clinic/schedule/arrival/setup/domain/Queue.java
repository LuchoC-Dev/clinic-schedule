package com.clinic.schedule.arrival.setup.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerJoined;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerLeft;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueCreated;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueRenamed;
import com.clinic.schedule.arrival.setup.domain.exception.QueueRuleViolationException;

/**
 * A queue as it was set up (D-62): its name, its consulting room if it has one, and the
 * practitioners who serve it — one for a practitioner's own room, several for a shared one such
 * as an emergency desk. Who waits in it is each session's business, not this.
 *
 * <p>It is its events. {@link #replay} adds them up into the queue they describe, and deciding
 * something about it answers with the event that says so, without changing this queue: applying
 * the event gives the new one. So a rule that says no leaves nothing half-done, and what was
 * decided is exactly what is kept.
 *
 * <p>The decisions are package-private: the rules that look at other queues — a name nobody else
 * has, a practitioner serving no other queue — are {@link QueueSetupService}'s, the only way in
 * (D-17). The shape of each value is checked here, on the event as it is applied, so a queue read
 * back from its events obeys the same rules as one being set up.
 */
public final class Queue {

    /** As long as the columns of the projection that lists them (D-38). */
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_ROOM_LENGTH = 20;

    private final QueueId id;
    private final String name;
    private final String room;
    private final Set<UUID> practitioners;
    private final int version;

    private Queue(QueueId id, String name, String room, Set<UUID> practitioners, int version) {
        this.id = id;
        this.name = name;
        this.room = room;
        this.practitioners = practitioners;
        this.version = version;
    }

    /**
     * The queue its events describe, oldest first.
     *
     * @throws IllegalArgumentException when they do not start with its creation, or describe
     *         something no queue could be
     */
    public static Queue replay(QueueId id, List<QueueEvent> events) {
        if (id == null || events == null || events.isEmpty() || !(events.get(0) instanceof QueueCreated created)) {
            throw new IllegalArgumentException("A queue starts with its creation");
        }
        Queue queue = new Queue(id, requireName(created.name()), cleanRoom(created.room()), Set.of(), 1);
        for (QueueEvent event : events.subList(1, events.size())) {
            queue = queue.apply(event);
        }
        return queue;
    }

    /** This queue after the event: a new one, since a queue never changes. */
    public Queue apply(QueueEvent event) {
        return switch (event) {
            case QueueCreated created -> throw new IllegalArgumentException("A queue is created only once");
            case QueueRenamed renamed ->
                    new Queue(id, requireName(renamed.name()), cleanRoom(renamed.room()), practitioners, version + 1);
            case PractitionerJoined joined -> {
                Set<UUID> after = new LinkedHashSet<>(practitioners);
                if (!after.add(joined.practitionerId())) {
                    throw new IllegalArgumentException("A practitioner joins a queue once");
                }
                yield new Queue(id, name, room, Collections.unmodifiableSet(after), version + 1);
            }
            case PractitionerLeft left -> {
                Set<UUID> after = new LinkedHashSet<>(practitioners);
                if (!after.remove(left.practitionerId())) {
                    throw new IllegalArgumentException("Only a practitioner of the queue leaves it");
                }
                yield new Queue(id, name, room, Collections.unmodifiableSet(after), version + 1);
            }
        };
    }

    static QueueCreated create(String name, String room) {
        return new QueueCreated(requireName(name), cleanRoom(room));
    }

    /** Refused when the name and the room stay as they are: there would be nothing to record. */
    QueueRenamed rename(String newName, String newRoom) {
        String cleanName = requireName(newName);
        String cleanRoom = cleanRoom(newRoom);
        if (cleanName.equals(name) && Objects.equals(cleanRoom, room)) {
            throw new QueueRuleViolationException("The queue is already called that, in that room");
        }
        return new QueueRenamed(cleanName, cleanRoom);
    }

    PractitionerJoined join(UUID practitionerId) {
        if (practitionerId == null) {
            throw new IllegalArgumentException("A practitioner is needed to join a queue");
        }
        if (practitioners.contains(practitionerId)) {
            throw new QueueRuleViolationException("That practitioner already serves this queue");
        }
        return new PractitionerJoined(practitionerId);
    }

    PractitionerLeft leave(UUID practitionerId) {
        if (!practitioners.contains(practitionerId)) {
            throw new QueueRuleViolationException("That practitioner does not serve this queue");
        }
        return new PractitionerLeft(practitionerId);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A queue needs a name");
        }
        String stripped = name.strip();
        if (stripped.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A queue's name is at most " + MAX_NAME_LENGTH + " characters");
        }
        return stripped;
    }

    /** A room is optional: blank is none. */
    private static String cleanRoom(String room) {
        if (room == null || room.isBlank()) {
            return null;
        }
        String stripped = room.strip();
        if (stripped.length() > MAX_ROOM_LENGTH) {
            throw new IllegalArgumentException("A consulting room is at most " + MAX_ROOM_LENGTH + " characters");
        }
        return stripped;
    }

    public QueueId id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Null when the queue has no consulting room of its own. */
    public String room() {
        return room;
    }

    /** Who serves it, in the order they joined. Empty is allowed: the queue waits for someone. */
    public Set<UUID> practitioners() {
        return practitioners;
    }

    /** How many events it is made of: where the next one goes. */
    public int version() {
        return version;
    }
}
