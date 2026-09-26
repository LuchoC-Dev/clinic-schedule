package com.clinic.schedule.arrival.setup;

import com.clinic.schedule.arrival.setup.domain.QueueId;

/** No queue has that id. Raised by the commands and the reads alike; maps to 404. */
public class QueueNotFoundException extends RuntimeException {

    public QueueNotFoundException(QueueId id) {
        super("No queue with id " + id.value());
    }
}
