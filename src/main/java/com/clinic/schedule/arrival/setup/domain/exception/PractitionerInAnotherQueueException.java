package com.clinic.schedule.arrival.setup.domain.exception;

/** The practitioner already serves another queue, and serves one at most. */
public class PractitionerInAnotherQueueException extends QueueRuleViolationException {

    public PractitionerInAnotherQueueException() {
        super("That practitioner already serves another queue; take them out of it first");
    }
}
