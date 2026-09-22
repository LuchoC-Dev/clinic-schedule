package com.clinic.schedule.appointment.domain;

import java.util.UUID;

/** Reference to a practitioner living in another module. See {@link PatientId}. */
public record PractitionerId(UUID value) {

    public PractitionerId {
        if (value == null) {
            throw new IllegalArgumentException("An appointment needs a practitioner");
        }
    }

    public static PractitionerId newId() {
        return new PractitionerId(UUID.randomUUID());
    }

    public static PractitionerId of(String value) {
        return new PractitionerId(UUID.fromString(value));
    }
}
