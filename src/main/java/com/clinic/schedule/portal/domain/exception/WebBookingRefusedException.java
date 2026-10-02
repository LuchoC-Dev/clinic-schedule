package com.clinic.schedule.portal.domain.exception;

/**
 * An appointment a patient may not book on the web, and why (D-82). Maps to 409: the request is well formed,
 * and refused for what the practice offers or what the patient already has.
 */
public class WebBookingRefusedException extends RuntimeException {

    public enum Reason {
        /** Reception does not let patients book that reason on the web, or there is no such reason. */
        NOT_ON_THE_WEB,
        /** Not between tomorrow and as far ahead as the web books. */
        DAY_OUT_OF_RANGE,
        /** Not one of the times the agenda offers that day. */
        NOT_AN_OFFERED_TIME,
        /** The patient has as many appointments ahead as the web lets them book. */
        TOO_MANY_AHEAD,
        /** Someone took the time meanwhile. */
        TIME_TAKEN,
        /** The patient has another appointment at that time. */
        PATIENT_BUSY
    }

    private final Reason reason;

    public WebBookingRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
