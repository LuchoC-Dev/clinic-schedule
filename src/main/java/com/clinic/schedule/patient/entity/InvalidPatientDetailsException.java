package com.clinic.schedule.patient.entity;

/**
 * A date of birth, a phone, an email or the person they belong to that cannot be right, refused
 * where it is typed (D-38, D-73). An {@link IllegalArgumentException}, so the API answers it as any
 * other malformed request; its own type lets a screen say what to fix in the patient's details
 * rather than in their identity.
 */
public class InvalidPatientDetailsException extends IllegalArgumentException {

    public InvalidPatientDetailsException(String message) {
        super(message);
    }
}
