package com.clinic.schedule.arrival.session.http;

import java.math.BigDecimal;
import java.util.UUID;

import com.clinic.schedule.cashdesk.api.Coverage;
import com.clinic.schedule.cashdesk.api.Means;

import jakarta.validation.constraints.NotNull;

/**
 * The patient reception brings into a queue, whether they are an emergency — left out, they are not —, and
 * the pesos charged, which only an emergency may leave out (D-87), and how they were paid — left out, in cash
 * (D-89). With {@code coverage}, the patient's insurance covers the visit, and the pesos are its copay, if any
 * (D-90); left out, they pay privately.
 */
record AddPatientRequest(@NotNull(message = "the patient is required") UUID patientId, Boolean emergency,
        BigDecimal amount, Means means, Coverage coverage) {

    boolean isEmergency() {
        return Boolean.TRUE.equals(emergency);
    }
}
