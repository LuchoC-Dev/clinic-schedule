package com.clinic.schedule.arrival.session.command;

/** The practitioner serves no queue, so there is nobody for them to call. Maps to 409. */
public class NoQueueServedException extends RuntimeException {

    public NoQueueServedException() {
        super("You serve no queue");
    }
}
