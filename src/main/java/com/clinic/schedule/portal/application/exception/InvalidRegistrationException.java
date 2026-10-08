package com.clinic.schedule.portal.application.exception;

/**
 * A registration made on the web that the patient module refused: a name, a document, a date of birth or a phone
 * that cannot be right (D-91). Maps to 400.
 */
public class InvalidRegistrationException extends RuntimeException {

    public InvalidRegistrationException(String message) {
        super(message);
    }
}
