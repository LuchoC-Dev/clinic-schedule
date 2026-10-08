package com.clinic.schedule.portal.application.exception;

/**
 * An account asked to register as many children in a day as the web takes (D-92). Maps to 429: it may try again
 * tomorrow, or come to reception.
 */
public class ChildRequestsExceededException extends RuntimeException {

    public ChildRequestsExceededException(int perDay) {
        super("An account asks to register up to " + perDay + " children a day");
    }
}
