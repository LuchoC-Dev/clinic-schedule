package com.clinic.schedule.appointment.domain;

/**
 * Reference to a patient living in another module. The domain only carries the identifier:
 * it never sees the patient entity (D-05).
 *
 * <p>It is a wrapper and not a bare {@code Long} so that a patient id and a practitioner id
 * cannot be swapped by accident, which the compiler would happily allow otherwise.
 */
public record PatientId(Long value) {

    public PatientId {
        if (value == null) {
            throw new IllegalArgumentException("An appointment needs a patient");
        }
    }
}
