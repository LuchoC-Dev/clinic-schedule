package com.clinic.schedule.portal.application.exception;

import java.util.UUID;

/** An account asked for a patient who does not exist. Maps to 400: the request names nobody. */
public class UnknownPatientException extends RuntimeException {

    public UnknownPatientException(UUID patientId) {
        super("No patient " + patientId);
    }
}
