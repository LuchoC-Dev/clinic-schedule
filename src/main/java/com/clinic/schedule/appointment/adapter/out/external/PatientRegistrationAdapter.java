package com.clinic.schedule.appointment.adapter.out.external;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.application.port.PatientRegistrationPort;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.patient.api.PatientWriter;

/**
 * Answers this module's {@link PatientRegistrationPort} by asking the patient module through its api
 * (D-05, D-75). The patient module's refusal passes through as it is: the screen that typed the
 * patient is the one that says what to fix.
 */
@Component
class PatientRegistrationAdapter implements PatientRegistrationPort {

    private final PatientWriter patients;

    PatientRegistrationAdapter(PatientWriter patients) {
        this.patients = patients;
    }

    @Override
    public PatientId register(NewPatient patient) {
        return new PatientId(patients.register(new com.clinic.schedule.patient.api.NewPatient(
                patient.fullName(), patient.documentType(), patient.documentNumber(),
                patient.phoneNumber(), patient.phoneOwnerRelation(), patient.phoneOwnerName(),
                patient.emailAddress(), patient.emailOwnerRelation(), patient.emailOwnerName())));
    }
}
