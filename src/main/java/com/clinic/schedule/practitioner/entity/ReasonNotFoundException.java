package com.clinic.schedule.practitioner.entity;

import java.util.UUID;

/** The practitioner has no reason with that id. Maps to 404. */
public class ReasonNotFoundException extends RuntimeException {

    ReasonNotFoundException(UUID practitionerId, UUID reasonId) {
        super("Practitioner %s has no reason with id %s".formatted(practitionerId, reasonId));
    }
}
