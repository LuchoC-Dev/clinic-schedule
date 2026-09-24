package com.clinic.schedule.consultation.domain;

import java.util.UUID;

/** Reference to a practitioner living in another module; see {@link PatientId}. */
public record PractitionerId(UUID value) {

    public PractitionerId {
        if (value == null) {
            throw new IllegalArgumentException("A consultation needs a practitioner");
        }
    }
}
