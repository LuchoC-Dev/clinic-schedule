package com.clinic.schedule.appointment.application;

import java.util.UUID;

import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * The practitioner has no reason with that id: it does not exist, or it is someone else's.
 * Maps to 400, since the request names something that cannot be booked.
 */
public class UnknownReasonException extends RuntimeException {

    public UnknownReasonException(PractitionerId practitionerId, UUID reasonId) {
        super("Practitioner %s has no reason with id %s".formatted(practitionerId.value(), reasonId));
    }
}
