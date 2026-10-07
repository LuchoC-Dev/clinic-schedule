package com.clinic.schedule.arrival.session.http;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * The patient reception brings into a queue, whether they are an emergency — left out, they are not —, and
 * the pesos charged, which only an emergency may leave out (D-87).
 */
record AddPatientRequest(@NotNull(message = "the patient is required") UUID patientId, Boolean emergency,
        BigDecimal amount) {

    boolean isEmergency() {
        return Boolean.TRUE.equals(emergency);
    }
}
