package com.clinic.schedule.appointment.domain;

/** Reference to a practitioner living in another module. See {@link PatientId}. */
public record PractitionerId(Long value) {

    public PractitionerId {
        if (value == null) {
            throw new IllegalArgumentException("An appointment needs a practitioner");
        }
    }
}
