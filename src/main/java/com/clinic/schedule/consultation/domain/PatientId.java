package com.clinic.schedule.consultation.domain;

import java.util.UUID;

/**
 * Reference to a patient living in another module. This module keeps its own wrapper, as
 * {@code appointment} does, so a patient id and a practitioner id cannot be swapped by accident.
 */
public record PatientId(UUID value) {

    public PatientId {
        if (value == null) {
            throw new IllegalArgumentException("A consultation needs a patient");
        }
    }
}
