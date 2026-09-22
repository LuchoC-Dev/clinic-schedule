package com.clinic.schedule.appointment.domain;

import java.util.UUID;

/**
 * Reference to a patient living in another module. The domain only carries the identifier:
 * it never sees the patient entity (D-05).
 *
 * <p>It is a wrapper and not a bare {@link UUID} so that a patient id and a practitioner id
 * cannot be swapped by accident, which the compiler would happily allow otherwise.
 */
public record PatientId(UUID value) {

    public PatientId {
        if (value == null) {
            throw new IllegalArgumentException("An appointment needs a patient");
        }
    }

    public static PatientId newId() {
        return new PatientId(UUID.randomUUID());
    }

    public static PatientId of(String value) {
        return new PatientId(UUID.fromString(value));
    }
}
