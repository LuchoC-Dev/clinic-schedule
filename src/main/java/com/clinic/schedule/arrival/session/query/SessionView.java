package com.clinic.schedule.arrival.session.query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.arrival.session.domain.Entry;

/**
 * An open queue session as the reads show it (D-63).
 *
 * @param waiting who waits, in the order they will be called
 * @param uncallable who waits and nobody may call: kept for a practitioner no longer serving the queue
 * @param called who was called and is being seen
 * @param done everyone else who came in: seen, left, did not show up
 */
public record SessionView(UUID sessionId, UUID queueId, LocalDateTime openedAt, boolean closesByItself,
        List<Entry> waiting, List<Entry> uncallable, List<Entry> called, List<Entry> done) {
}
