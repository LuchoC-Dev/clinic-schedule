package com.clinic.schedule.arrival.session.domain.exception;

/** Only a practitioner who serves a queue calls its patients. */
public class NotServingThisQueueException extends SessionRuleViolationException {

    public NotServingThisQueueException() {
        super(Reason.NOT_SERVING);
    }
}
