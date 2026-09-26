package com.clinic.schedule.arrival.setup.domain.exception;

/** Another queue already has that name. */
public class QueueNameTakenException extends QueueRuleViolationException {

    public QueueNameTakenException(String name) {
        super("Another queue is already called " + name);
    }
}
