package com.clinic.schedule.arrival.setup.query;

import java.util.List;
import java.util.UUID;

/**
 * A queue as it is set up now, read from the projection (D-62).
 *
 * @param room null for a queue with no consulting room of its own
 * @param practitioners who serve it; empty is allowed
 */
public record QueueView(UUID id, String name, String room, List<UUID> practitioners) {
}
