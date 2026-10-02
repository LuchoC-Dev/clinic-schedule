package com.clinic.schedule.portal.domain.exception;

/** An email that cannot be one. An {@link IllegalArgumentException}, so the API answers 400. */
public class InvalidAccountEmailException extends IllegalArgumentException {

    public InvalidAccountEmailException(String message) {
        super(message);
    }
}
