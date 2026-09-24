package com.clinic.schedule.appointment.adapter.out.external;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.application.port.PatientRegistryPort;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.patient.api.PatientReader;

/**
 * Answers this module's {@link PatientRegistryPort} by asking the patient module through its
 * api. With {@link PractitionerScheduleAdapter}, the only classes here importing from
 * another module, and only from its {@code api/} (D-05).
 */
@Component
class PatientRegistryAdapter implements PatientRegistryPort {

    private final PatientReader patients;

    PatientRegistryAdapter(PatientReader patients) {
        this.patients = patients;
    }

    @Override
    public boolean exists(PatientId patientId) {
        return patients.exists(patientId.value());
    }
}
