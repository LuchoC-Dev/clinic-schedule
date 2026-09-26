package com.clinic.schedule.arrival.setup.domain.exception;

/** What was asked of a queue's setup goes against its rules. Maps to 409, like every business rule. */
public class QueueRuleViolationException extends RuntimeException {

    public QueueRuleViolationException(String message) {
        super(message);
    }
}
