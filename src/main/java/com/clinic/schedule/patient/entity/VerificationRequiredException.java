package com.clinic.schedule.patient.entity;

/**
 * A change that would leave a patient with a web account unverified: their account logs in with their
 * document, so a new name or document needs the document seen. Maps to 409, since the request is well
 * formed and refused for the patient's state.
 */
public class VerificationRequiredException extends RuntimeException {

    VerificationRequiredException(String message) {
        super(message);
    }
}
