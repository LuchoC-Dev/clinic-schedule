package com.clinic.schedule.arrival.session.domain.exception;

/**
 * What was asked of a queue session goes against its rules. Maps to 409, like every business rule.
 * It says which rule, so a screen can put it in its own words without reading the message.
 */
public class SessionRuleViolationException extends RuntimeException {

    /** The rules of a session, each with how the API says it was broken. */
    public enum Reason {
        QUEUE_ALREADY_OPEN("The queue is already open"),
        NOT_SERVING("You do not serve this queue"),
        ALREADY_WAITING("That patient is already waiting in this queue"),
        ALREADY_ARRIVED("The patient of that appointment already came in"),
        ALREADY_KEPT_FOR_THEM("That patient is already kept for that practitioner"),
        NOT_KEPT("That patient is kept for nobody"),
        ALREADY_EMERGENCY("That patient is already an emergency"),
        NOT_WAITING("That patient is no longer waiting"),
        NOBODY_WAITING("Nobody is waiting"),
        NOBODY_CALLED("You have no patient called"),
        STILL_CALLED("Finish with the patient called, or say they did not come in, first"),
        AUTO_CLOSE_ALREADY_OFF("The queue already stays open past the end of its day");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public SessionRuleViolationException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
