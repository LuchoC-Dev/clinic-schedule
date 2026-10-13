package com.clinic.schedule.portal.domain.exception;

/**
 * An appointment a patient may not move on the web to the time they asked for, and why (D-93). Maps to 409: the
 * request is well formed, and refused for the appointment's state, its time or what the agenda offers.
 */
public class WebMoveRefusedException extends RuntimeException {

    public enum Reason {
        /** It is cancelled or absent already. */
        NOT_BOOKED,
        /** There is less time left before it than the web cancels with. */
        TOO_LATE,
        /** The new day is not between tomorrow and as far ahead as the web books. */
        DAY_OUT_OF_RANGE,
        /** Not one of the times the agenda offers to move it to that day. */
        NOT_AN_OFFERED_TIME,
        /** The time it already has. */
        SAME_TIME,
        /** Someone took the time meanwhile. */
        TIME_TAKEN,
        /** The patient has another appointment at that time. */
        PATIENT_BUSY
    }

    private final Reason reason;

    public WebMoveRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
