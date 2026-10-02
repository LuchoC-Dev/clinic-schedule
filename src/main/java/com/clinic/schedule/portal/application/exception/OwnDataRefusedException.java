package com.clinic.schedule.portal.application.exception;

/**
 * A change a patient asked for their own data that the patient module refused: a value that cannot be
 * right, or agreeing to be reminded on a channel that is not there. Maps to 400.
 */
public class OwnDataRefusedException extends RuntimeException {

    public OwnDataRefusedException(String message) {
        super(message);
    }
}
