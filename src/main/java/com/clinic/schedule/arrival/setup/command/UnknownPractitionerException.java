package com.clinic.schedule.arrival.setup.command;

import java.util.UUID;

/** No practitioner has that id, so nobody can join a queue under it. Maps to 400, like a booking's. */
public class UnknownPractitionerException extends RuntimeException {

    UnknownPractitionerException(UUID practitionerId) {
        super("No practitioner with id " + practitionerId);
    }
}
