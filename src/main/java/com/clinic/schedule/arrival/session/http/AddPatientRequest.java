package com.clinic.schedule.arrival.session.http;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The patient reception brings into a queue, and whether they are an emergency; left out, they are not. */
record AddPatientRequest(@NotNull(message = "the patient is required") UUID patientId, Boolean emergency) {

    boolean isEmergency() {
        return Boolean.TRUE.equals(emergency);
    }
}
