package com.clinic.schedule.arrival.api;

import java.util.List;
import java.util.UUID;

/**
 * A patient's place in one queue, as other modules see it (D-63).
 *
 * @param servedBy who serves the queue now, and so may call them while they wait
 * @param waiting whether they are waiting to be called
 * @param withPractitioner who called or saw them that day; null while they wait, or after they did
 *        not come in
 */
public record QueueArrival(UUID queueId, List<UUID> servedBy, boolean waiting, UUID withPractitioner) {
}
