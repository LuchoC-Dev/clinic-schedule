package com.clinic.schedule.arrival.session.command;

/** The queue has no open session: it is opened before anyone comes in or is called. Maps to 409. */
public class QueueNotOpenException extends RuntimeException {

    QueueNotOpenException() {
        super("The queue is not open");
    }
}
