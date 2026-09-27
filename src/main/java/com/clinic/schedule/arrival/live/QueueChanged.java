package com.clinic.schedule.arrival.live;

import java.util.UUID;

/**
 * Something changed in a queue — how it is set up, or who is in it — so whoever shows it should
 * show it again (D-66). An application event of this module, published by the commands; it says
 * which queue and nothing more, so no screen learns anything it could not read.
 */
public record QueueChanged(UUID queueId) {
}
