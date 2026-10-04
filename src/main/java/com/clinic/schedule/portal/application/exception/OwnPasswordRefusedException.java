package com.clinic.schedule.portal.application.exception;

/**
 * A patient's change of their own password refused, and why (D-86): the current one was not theirs, or the new
 * one is the same. Maps to 400, as the staff's own change (D-69).
 */
public class OwnPasswordRefusedException extends RuntimeException {

    public enum Reason {
        WRONG_CURRENT,
        SAME
    }

    private final Reason reason;

    public OwnPasswordRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
