package com.clinic.schedule.appointment.application;

import java.util.HashSet;
import java.util.Set;

import com.clinic.schedule.appointment.domain.PatientId;

/** The patient port answered from memory: a patient exists once the test registers them. */
class FakePatientRegistry implements PatientRegistryPort {

    private final Set<PatientId> registered = new HashSet<>();

    PatientId register(PatientId patientId) {
        registered.add(patientId);
        return patientId;
    }

    @Override
    public boolean exists(PatientId patientId) {
        return registered.contains(patientId);
    }
}
