package com.clinic.schedule.practitioner;

import java.util.UUID;

/** The practitioner has no reason with that id. Maps to 404. */
class ReasonNotFoundException extends RuntimeException {

    ReasonNotFoundException(UUID practitionerId, UUID reasonId) {
        super("Practitioner %s has no reason with id %s".formatted(practitionerId, reasonId));
    }
}
