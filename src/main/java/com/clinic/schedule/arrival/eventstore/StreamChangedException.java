package com.clinic.schedule.arrival.eventstore;

import java.util.UUID;

/** Someone else added to the stream since it was read: what was decided on it no longer holds. */
public class StreamChangedException extends RuntimeException {

    StreamChangedException(UUID streamId) {
        super("It changed while this was being done; read it again and retry: " + streamId);
    }
}
