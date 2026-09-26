package com.clinic.schedule.arrival.eventstore;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An event as it was kept: which stream, its place in it, its type and payload, and when and by
 * whom it was recorded.
 */
public record StoredEvent(UUID streamId, int version, String type, String payload, LocalDateTime recordedAt,
        String recordedBy) {
}
