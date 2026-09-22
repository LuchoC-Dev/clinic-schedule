package com.clinic.schedule.patient;

import java.util.UUID;

/** A patient as the API returns it. A patient without a national ID returns it as null. */
record PatientResponse(UUID id, String fullName, String nationalId) {

    static PatientResponse of(Patient patient) {
        return new PatientResponse(patient.getId(), patient.getFullName(), patient.getNationalId());
    }
}
