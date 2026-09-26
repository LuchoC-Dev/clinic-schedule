package com.clinic.schedule.arrival.session.command;

import java.util.UUID;

/** No patient has that id, so nobody comes into the queue under it. Maps to 400, like a booking's. */
public class UnknownPatientException extends RuntimeException {

    UnknownPatientException(UUID patientId) {
        super("No patient with id " + patientId);
    }
}
