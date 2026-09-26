package com.clinic.schedule.arrival.session.domain.exception;

/** The queue already has an open session: it is closed before it opens again. */
public class QueueAlreadyOpenException extends SessionRuleViolationException {

    public QueueAlreadyOpenException() {
        super(Reason.QUEUE_ALREADY_OPEN);
    }
}
