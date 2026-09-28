package com.clinic.schedule.user.service;

import java.util.UUID;

/** The practitioner has an active user already, and has one at most (D-68). */
public class PractitionerTakenException extends RuntimeException {

    public PractitionerTakenException(UUID practitionerId) {
        super("The practitioner " + practitionerId + " has an active user already");
    }
}
